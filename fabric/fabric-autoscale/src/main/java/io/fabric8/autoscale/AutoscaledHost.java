package io.fabric8.autoscale;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.fabric8.api.Profile;

public class AutoscaledHost implements ProfileContainer {

    private final String hostId;
    private final Map<String, AutoscaledContainer> containerMap = new HashMap<>();

    public AutoscaledHost(String hostId) {
        this.hostId = hostId;
    }

    public void addAutoscaledContainer(AutoscaledContainer container) {
        containerMap.put(container.getId(), container);
    }

    @Override
    public boolean hasProfile(Profile profile) {
        return hasProfile(profile.getId());
    }

    @Override
    public boolean hasProfile(String profileId) {
        for (AutoscaledContainer container : containerMap.values()) {
            if (container.hasProfile(profileId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addProfile(Profile profile) throws Exception {
        addProfile(profile.getId());
    }

    @Override
    public void addProfile(String profileId) throws Exception {
        List<AutoscaledContainer> autoscaledContainerList = new LinkedList<>(containerMap.values());
        Collections.sort(autoscaledContainerList, new SortAutoscaledContainersByProfileCount());
        for (AutoscaledContainer container : autoscaledContainerList) {
            if (!container.hasProfile(profileId)) {
                container.addProfile(profileId);
                return;
            }
        }
        throw new Exception("Couldn't add profile to host " + hostId);
    }

    @Override
    public void removeProfile(Profile profile) {
        removeProfile(profile, 1);
    }

    @Override
    public void removeProfile(String profileId) {
        removeProfile(profileId, 1);
    }

    @Override
    public void removeProfile(Profile profile, int count) {
        removeProfile(profile.getId(), count);
    }

    @Override
    public void removeProfile(String profileId, int count) {
        List<AutoscaledContainer> autoscaledContainerList = new LinkedList<>(containerMap.values());
        Collections.sort(autoscaledContainerList, new SortAutoscaledContainersByProfileCount());
        Collections.reverse(autoscaledContainerList);
        Iterator<AutoscaledContainer> iterator = autoscaledContainerList.iterator();
        for (int i = count; i > 0 && iterator.hasNext();) {
            AutoscaledContainer container = iterator.next();
            if (container.hasProfile(profileId)) {
                container.removeProfile(profileId);
                i--;
            }
        }
    }

    @Override
    public int getProfileCount() {
        int count = 0;
        for (AutoscaledContainer container : containerMap.values()) {
            count = count + container.getProfileCount();
        }
        return count;
    }

    @Override
    public int getProfileCount(Profile profile) {
        return getProfileCount(profile.getId());
    }

    @Override
    public int getProfileCount(String profileId) {
        int count = 0;
        for (AutoscaledContainer container : containerMap.values()) {
            count = count + container.getProfileCount(profileId);
        }
        return count;
    }

    @Override
    public String getId() {
        return hostId;
    }

    private class SortAutoscaledContainersByProfileCount implements Comparator<AutoscaledContainer> {
        @Override
        public int compare(AutoscaledContainer autoscaledContainer, AutoscaledContainer t1) {
            return autoscaledContainer.getProfileCount() - t1.getProfileCount();
        }
    }
}
