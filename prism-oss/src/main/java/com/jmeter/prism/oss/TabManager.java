package com.jmeter.prism.oss;

public interface TabManager {
    void initialize();
    void openNewTab();
    int getTabCount();
    int getMaxTabs();
}
