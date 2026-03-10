package com.jmeter.prism.oss;

import java.util.ServiceLoader;

public class TabManagerFactory {
    private static TabManager instance;

    public static synchronized TabManager getInstance() {
        if (instance == null) {
            // First check if a ProTabManager is available via ServiceLoader
            ServiceLoader<TabManager> serviceLoader = ServiceLoader.load(TabManager.class);
            for (TabManager manager : serviceLoader) {
                // Return the first one found that isn't the default OSS one, or simply the first found if prioritization is set up
                if (!manager.getClass().equals(OssTabManager.class)) {
                    instance = manager;
                    break;
                }
            }
            
            // Fallback to OSS if no Pro is found
            if (instance == null) {
                instance = new OssTabManager();
            }
            instance.initialize();
        }
        return instance;
    }
}
