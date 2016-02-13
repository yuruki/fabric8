package io.fabric8.autoscale;

import java.util.HashMap;
import java.util.Map;

public class AutoscaledHost {

    private final String hostId;
    private final Map<String, AutoscaledContainer> containerMap = new HashMap<>();

    public AutoscaledHost(String hostId) {
        this.hostId = hostId;
    }

    public void addAutoscaledContainer(AutoscaledContainer container) {
        containerMap.put(container.getId(), container);
    }
}
