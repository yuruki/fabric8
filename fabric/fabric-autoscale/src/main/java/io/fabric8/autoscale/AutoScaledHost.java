package io.fabric8.autoscale;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import io.fabric8.api.Container;
import io.fabric8.api.ProfileRequirements;

public class AutoScaledHost extends ProfileContainer {

    private final List<ProfileContainer> containerList = new LinkedList<>();
    private final Container rootContainer;

    public AutoScaledHost(String id, Container rootContainer) {
        this.id = id;
        this.rootContainer = rootContainer;
    }

    public void addProfileContainer(ProfileContainer container) {
        containerList.add(container);
    }

    @Override
    public boolean hasProfile(String profileId) {
        for (ProfileContainer container : containerList) {
            if (container.hasProfile(profileId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addProfile(ProfileRequirements profile, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Collections.sort(containerList, new SortProfileContainers());
            Exception exception = null;
            for (ProfileContainer container : containerList) {
                try {
                    container.addProfile(profile);
                    break;
                } catch (Exception e) {
                    exception = e;
                }
            }
            if (exception != null) {
                throw new Exception("Couldn't add profile " + profile.getProfile() + " to host " + id, exception);
            }
        }
    }

    @Override
    public void removeProfile(String profile, int count) {
        for (int i = 0; i < count; i++) {
            Collections.sort(containerList, new SortProfileContainers());
            Collections.reverse(containerList);
            for (ProfileContainer container : containerList) {
                if (container.hasProfile(profile)) {
                    container.removeProfile(profile);
                    break;
                }
            }
        }
    }

    @Override
    public int getProfileCount() {
        int count = 0;
        for (ProfileContainer container : containerList) {
            count += container.getProfileCount();
        }
        return count;
    }

    @Override
    public int getProfileCount(String profileId) {
        int count = 0;
        for (ProfileContainer container : containerList) {
            count += container.getProfileCount(profileId);
        }
        return count;
    }

    public void removeProfileContainer(ProfileContainer container) {
        containerList.remove(container);
    }

    private class SortProfileContainers implements Comparator<ProfileContainer> {
        @Override
        public int compare(ProfileContainer container, ProfileContainer t1) {
            return container.getProfileCount() - t1.getProfileCount();
        }
    }
}
