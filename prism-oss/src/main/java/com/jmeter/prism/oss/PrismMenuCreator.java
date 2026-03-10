package com.jmeter.prism.oss;

import org.apache.jmeter.gui.plugin.MenuCreator;
import org.apache.jmeter.gui.GuiPackage;

import javax.swing.*;

public class PrismMenuCreator implements MenuCreator {

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location == MENU_LOCATION.FILE) {
            TabManagerFactory.getInstance().initialize();
            JMenuItem newTabItem = new JMenuItem("New Tab (Prism)");
            newTabItem.addActionListener(e -> TabManagerFactory.getInstance().openNewTab());
            return new JMenuItem[] { newTabItem };
        }
        return new JMenuItem[0];
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }

    @Override
    public void localeChanged() {
    }
}
