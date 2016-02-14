package io.fabric8.autoscale;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AutoScaledHost extends ProfileContainer {

    private final String id;
    private final Map<String, AutoScaledContainer> containerMap = new HashMap<>();

    public AutoScaledHost(String id) {
        this.id = id;
    }

    public void addAutoScaledContainer(AutoScaledContainer container) {
        containerMap.put(container.getId(), container);
    }

    @Override
    public boolean hasProfile(String profileId) {
        for (AutoScaledContainer container : containerMap.values()) {
            if (container.hasProfile(profileId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addProfile(String profileId, int count) throws Exception {
        List<AutoScaledContainer> autoScaledContainerList = new LinkedList<>(containerMap.values());
        Collections.sort(autoScaledContainerList, new SortAutoScaledContainersByProfileCount());
        for (AutoScaledContainer container : autoScaledContainerList) {
            if (!container.hasProfile(profileId)) {
                container.addProfile(profileId);
                return;
            }
        }
        throw new Exception("Couldn't add profile to host " + id);
    }

    @Override
    public void removeProfile(String profileId, int count) {
        List<AutoScaledContainer> autoScaledContainerList = new LinkedList<>(containerMap.values());
        Collections.sort(autoScaledContainerList, new SortAutoScaledContainersByProfileCount());
        Collections.reverse(autoScaledContainerList);
        Iterator<AutoScaledContainer> iterator = autoScaledContainerList.iterator();
        for (int i = count; i > 0 && iterator.hasNext();) {
            AutoScaledContainer container = iterator.next();
            if (container.hasProfile(profileId)) {
                container.removeProfile(profileId);
                i--;
            }
        }
    }

    @Override
    public int getProfileCount() {
        int count = 0;
        for (AutoScaledContainer container : containerMap.values()) {
            count = count + container.getProfileCount();
        }
        return count;
    }

    @Override
    public int getProfileCount(String profileId) {
        int count = 0;
        for (AutoScaledContainer container : containerMap.values()) {
            count = count + container.getProfileCount(profileId);
        }
        return count;
    }

    public void removeAutoScaledContainer(AutoScaledContainer container) {
        containerMap.remove(container.getId());
    }

    private class SortAutoScaledContainersByProfileCount implements Comparator<AutoScaledContainer> {
        @Override
        public int compare(AutoScaledContainer autoScaledContainer, AutoScaledContainer t1) {
            return autoScaledContainer.getProfileCount() - t1.getProfileCount();
        }
    }
}
