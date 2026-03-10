package com.jmeter.prism.oss;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class OssTabManager implements TabManager {
    private static final Logger log = LoggerFactory.getLogger(OssTabManager.class);

    // Hardcoded limit for OSS version. No properties exposure.
    private static final int MAX_TABS = 2;

    private JTabbedPane tabbedPane;
    private List<WorkspaceState> tabStates = new ArrayList<>();
    private int currentTabIndex = -1;
    private boolean initialized = false;

    @Override
    public void initialize() {
        if (initialized)
            return;
        initialized = true;

        log.info("Initializing Prism OSS TabManager (Max Tabs: {})", MAX_TABS);

        // This is a simplified injection. In a real JMeter plugin, we would need to
        // carefully extract the MainFrame's split pane and wrap it in our JTabbedPane.
        SwingUtilities.invokeLater(() -> {
            try {
                GuiPackage guiPackage = GuiPackage.getInstance();
                if (guiPackage != null) {
                    MainFrame mainFrame = guiPackage.getMainFrame();
                    if (mainFrame != null) {
                        injectTabbedUI(mainFrame);

                        // Start a timer to periodically update the current tab's title
                        Timer titleUpdater = new Timer(1000, e1 -> {
                            if (currentTabIndex >= 0 && currentTabIndex < tabbedPane.getTabCount()) {
                                updateTabTitle(currentTabIndex);
                            }
                        });
                        titleUpdater.start();
                    }
                }
            } catch (Exception e) {
                log.error("Failed to inject Prism Tab UI", e);
            }
        });
    }

    private void updateTabTitle(int index) {
        WorkspaceState state = tabStates.get(index);
        String fullTitle = state.name;

        if (state.guiPackage != null) {
            String file = state.guiPackage.getTestPlanFile();
            if (file != null && !file.trim().isEmpty()) {
                fullTitle = new java.io.File(file).getName();
            } else {
                try {
                    org.apache.jmeter.gui.tree.JMeterTreeNode testPlanNode = (org.apache.jmeter.gui.tree.JMeterTreeNode) state.guiPackage
                            .getTreeModel().getRoot();
                    if (testPlanNode != null) {
                        fullTitle = testPlanNode.getName();
                    }
                } catch (Exception e) {
                    // Ignore, fallback to default
                }
            }
        }

        String displayTitle = fullTitle;
        if (displayTitle.length() > 20) {
            displayTitle = displayTitle.substring(0, 17) + "...";
        }

        if (!displayTitle.equals(tabbedPane.getTitleAt(index))) {
            tabbedPane.setTitleAt(index, displayTitle);
        }

        String currentTooltip = tabbedPane.getToolTipTextAt(index);
        if (currentTooltip == null || !currentTooltip.equals(fullTitle)) {
            tabbedPane.setToolTipTextAt(index, fullTitle);
        }
    }

    private void injectTabbedUI(MainFrame mainFrame) {
        // Create the TabbedPane
        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // Listen to tab changes
        tabbedPane.addChangeListener(e -> {
            if (currentTabIndex == tabbedPane.getSelectedIndex() || currentTabIndex == -1) {
                return;
            }
            switchTab(tabbedPane.getSelectedIndex());
        });

        // The default view in JMeter MainFrame is a JPanel "all" with BorderLayout.
        // It has a toolbar at NORTH and the main JSplitPane at CENTER.
        Component allObj = mainFrame.getContentPane().getComponent(0);
        if (allObj instanceof JPanel) {
            JPanel all = (JPanel) allObj;
            BorderLayout layout = (BorderLayout) all.getLayout();
            Component treeAndMain = layout.getLayoutComponent(BorderLayout.CENTER);
            if (treeAndMain != null) {
                all.remove(treeAndMain);

                // Add tabbed pane as navigation bar mapping to same content
                JPanel contentPanel = new JPanel(new BorderLayout());
                contentPanel.add(tabbedPane, BorderLayout.NORTH);
                contentPanel.add(treeAndMain, BorderLayout.CENTER);

                all.add(contentPanel, BorderLayout.CENTER);
            }
        }

        // Initialize Tab 1 with current GuiPackage state
        WorkspaceState state0 = new WorkspaceState("Test Plan");
        state0.guiPackage = GuiPackage.getInstance();
        tabStates.add(state0);
        currentTabIndex = 0;

        tabbedPane.addTab("Test Plan", new JPanel()); // Stub for UI button

        mainFrame.validate();
        mainFrame.repaint();
    }

    @Override
    public void openNewTab() {
        if (tabStates.size() >= getMaxTabs()) {
            JOptionPane.showMessageDialog(null,
                    "Maximum tabs reached in OSS version (" + getMaxTabs() + "). Upgrade to Pro for unlimited tabs.",
                    "Prism Plugin limit",
                    JOptionPane.WARNING_MESSAGE);
            throw new MaxTabsReachedException("Cannot open more than " + getMaxTabs() + " tabs in OSS version.");
        }

        int newIndex = tabStates.size();
        WorkspaceState newState = new WorkspaceState("Test Plan");
        tabStates.add(newState);

        tabbedPane.addTab(newState.name, new JPanel());
        tabbedPane.setSelectedIndex(newIndex); // This triggers ChangeListener to switchTab()

        log.info("Opened new tab. Total tabs: {}", tabStates.size());
    }

    private void switchTab(int targetIndex) {
        log.info("Switching to tab index {}", targetIndex);

        MainFrame mainFrame = GuiPackage.getInstance().getMainFrame();
        JTree tree = mainFrame.getTree();

        // 1. Save current state (including tree expansion)
        WorkspaceState oldState = tabStates.get(currentTabIndex);
        oldState.guiPackage = GuiPackage.getInstance();
        if (oldState.guiPackage != null) {
            oldState.guiPackage.updateCurrentNode();

            // Save tree expansion state as row indices
            oldState.expandedRows.clear();
            for (int i = 0; i < tree.getRowCount(); i++) {
                if (tree.isExpanded(i)) {
                    oldState.expandedRows.add(i);
                }
            }
            oldState.selectedRow = tree.getMinSelectionRow();

            // Extract the action listener from old tree listener so we can attach to new
            // one
            try {
                java.lang.reflect.Field actionHandlerField = org.apache.jmeter.gui.tree.JMeterTreeListener.class
                        .getDeclaredField("actionHandler");
                actionHandlerField.setAccessible(true);
                oldState.actionHandler = (java.awt.event.ActionListener) actionHandlerField
                        .get(oldState.guiPackage.getTreeListener());
            } catch (Exception e) {
                log.warn("Could not extract actionHandler from JMeterTreeListener", e);
            }
        }

        // 2. Prepare target state
        WorkspaceState targetState = tabStates.get(targetIndex);
        final boolean isNewlyCreated;
        if (targetState.guiPackage == null) {
            isNewlyCreated = true;
            // First time opening this tab: construct new GuiPackage
            org.apache.jmeter.gui.tree.JMeterTreeModel newModel = new org.apache.jmeter.gui.tree.JMeterTreeModel();
            newModel.clearTestPlan();
            org.apache.jmeter.gui.tree.JMeterTreeListener newListener = new org.apache.jmeter.gui.tree.JMeterTreeListener();
            newListener.setModel(newModel);
            if (oldState.actionHandler != null) {
                newListener.setActionHandler(oldState.actionHandler);
            }

            GuiPackage.initInstance(newListener, newModel);
            targetState.guiPackage = GuiPackage.getInstance();
            targetState.guiPackage.setMainFrame(oldState.guiPackage.getMainFrame());
            targetState.guiPackage.setMainToolbar(oldState.guiPackage.getMainToolbar());

            // Extract LoggerPanel
            try {
                java.lang.reflect.Field logPanelField = org.apache.jmeter.gui.MainFrame.class
                        .getDeclaredField("logPanel");
                logPanelField.setAccessible(true);
                targetState.guiPackage.setLoggerPanel(
                        (org.apache.jmeter.gui.LoggerPanel) logPanelField.get(targetState.guiPackage.getMainFrame()));
            } catch (Exception e) {
            }

            targetState.guiPackage.registerAsListener();
        } else {
            isNewlyCreated = false;
            // Restore existing GuiPackage
            try {
                java.lang.reflect.Field guiPackField = GuiPackage.class.getDeclaredField("guiPack");
                guiPackField.setAccessible(true);
                guiPackField.set(null, targetState.guiPackage);
            } catch (Exception e) {
                log.error("Failed to restore GuiPackage via reflection", e);
            }
        }

        // 3. Swap UI Pointers (JTree models and listeners)
        // Remove old listeners
        if (oldState.guiPackage != null) {
            tree.removeTreeSelectionListener(oldState.guiPackage.getTreeListener());
            tree.removeMouseListener(oldState.guiPackage.getTreeListener());
            tree.removeKeyListener(oldState.guiPackage.getTreeListener());
        }

        // Apply new model and listeners
        tree.setModel(targetState.guiPackage.getTreeModel());
        tree.addTreeSelectionListener(targetState.guiPackage.getTreeListener());
        tree.addMouseListener(targetState.guiPackage.getTreeListener());
        tree.addKeyListener(targetState.guiPackage.getTreeListener());
        targetState.guiPackage.getTreeListener().setJTree(tree);

        // 3a. Swap CheckDirty singleton state so JMeter doesn't mark the new tab as
        // dirty automatically
        swapCheckDirtyState(oldState, targetState);

        // 4. Update current tab index BEFORE restoring expansion
        currentTabIndex = targetIndex;

        // 5. Update JMeter's singleton FileServer base for the active tab to prevent
        // path corruption
        String activeFile = targetState.guiPackage.getTestPlanFile();
        try {
            if (activeFile != null && !activeFile.isEmpty()) {
                org.apache.jmeter.services.FileServer.getFileServer()
                        .setBaseForScript(new java.io.File(activeFile));
            } else {
                org.apache.jmeter.services.FileServer.getFileServer().resetBase();
            }
        } catch (Exception e) {
            log.warn("Failed to update FileServer base directory", e);
        }

        // 6. Restore tree expansion state for target tab using invokeLater
        // to ensure the model is fully committed before expanding
        final List<Integer> rowsToExpand = new ArrayList<>(targetState.expandedRows);
        final int rowToSelect = targetState.selectedRow;
        SwingUtilities.invokeLater(() -> {
            // Expand rows from top to bottom (order matters for JTree)
            for (int row : rowsToExpand) {
                if (row < tree.getRowCount()) {
                    tree.expandRow(row);
                }
            }
            // Restore selection
            if (rowToSelect >= 0 && rowToSelect < tree.getRowCount()) {
                tree.setSelectionRow(rowToSelect);
            } else {
                tree.setSelectionRow(1);
            }
            targetState.guiPackage.updateCurrentGui();
            mainFrame.setExtendedFrameTitle(targetState.guiPackage.getTestPlanFile());
            mainFrame.repaint();

            // Clear dirty flag for new tabs after ALL UI updates settle to prevent "Save?"
            // prompt
            if (isNewlyCreated) {
                SwingUtilities.invokeLater(() -> {
                    targetState.guiPackage.setDirty(false);
                });
            }
        });
    }

    @Override
    public int getTabCount() {
        return tabStates.size();
    }

    @Override
    public int getMaxTabs() {
        return MAX_TABS;
    }

    @SuppressWarnings("unchecked")
    private void swapCheckDirtyState(WorkspaceState oldState, WorkspaceState targetState) {
        try {
            org.apache.jmeter.gui.action.Command checkDirty = org.apache.jmeter.gui.action.ActionRouter.getInstance()
                    .getAction("check_dirty", org.apache.jmeter.gui.action.CheckDirty.class);
            if (checkDirty != null) {
                java.lang.reflect.Field itemsField = checkDirty.getClass().getDeclaredField("previousGuiItems");
                itemsField.setAccessible(true);

                // Save current CheckDirty state to old tab
                if (oldState != null) {
                    oldState.checkDirtyItems = (java.util.Map<Object, Object>) itemsField.get(checkDirty);
                }

                // Restore target state
                if (targetState.checkDirtyItems == null) {
                    targetState.checkDirtyItems = new java.util.HashMap<>();
                }
                itemsField.set(checkDirty, targetState.checkDirtyItems);
            }
        } catch (Exception e) {
            log.warn("Could not swap CheckDirty state", e);
        }
    }
}
