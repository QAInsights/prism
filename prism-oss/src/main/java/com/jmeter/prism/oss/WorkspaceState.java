package com.jmeter.prism.oss;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jmeter.gui.GuiPackage;

public class WorkspaceState {
    public GuiPackage guiPackage;
    public String name;
    public ActionListener actionHandler;

    /** Saved tree expanded row indices for restoring tree state */
    public List<Integer> expandedRows;

    /** Saved tree selected row index */
    public int selectedRow = -1;

    // Save CheckDirty singleton state
    public Map<Object, Object> checkDirtyItems;

    public WorkspaceState() {
        expandedRows = new ArrayList<>();
        checkDirtyItems = new HashMap<>();
    }

    public WorkspaceState(String name) {
        this(); // Call the default constructor to initialize lists/maps
        this.name = name;
    }
}
