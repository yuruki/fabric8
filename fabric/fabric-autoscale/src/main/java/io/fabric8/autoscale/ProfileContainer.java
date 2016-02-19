package io.fabric8.autoscale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.fabric8.api.Profile;
import io.fabric8.api.ProfileRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ProfileContainer {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());
    protected final Map<String, ProfileContainer> childMap = new HashMap<>();

    protected String id = "default";
    protected Boolean removable = true;
    protected Boolean removed = false;
    protected Comparator<ProfileContainer> childComparator = new SortByContainerCount();

    final public boolean hasProfile(Profile profile) {
        return hasProfile(profile.getId());
    }

    final public boolean hasProfile(ProfileRequirements profile) {
        return hasProfile(profile.getProfile());
    }

    final public void addChild(ProfileContainer child) {
        childMap.put(child.getId(), child);
    }

    public boolean hasProfile(String profileId) {
        for (ProfileContainer child : childMap.values()) {
            if (child.hasProfile(profileId)) {
                return true;
            }
        }
        return false;
    }

    final public void addProfile(ProfileRequirements profile) throws Exception {
        addProfile(profile, 1);
    }

    public void addProfile(ProfileRequirements profile, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            List<ProfileContainer> children = new LinkedList<>(getSortedChildren());
            Exception exception = null;
            for (ProfileContainer child : children) {
                try {
                    child.addProfile(profile);
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

    final public void removeProfile(Profile profile) {
        removeProfile(profile.getId(), 1);
    }

    final public void removeProfile(String profileId) {
        removeProfile(profileId, 1);
    }

    final public void removeProfile(ProfileRequirements profile) {
        removeProfile(profile.getProfile(), 1);
    }

    final public void removeProfile(ProfileRequirements profile, int count) {
        removeProfile(profile.getProfile(), count);
    }

    public void removeProfile(String profile, int count) {
        for (int i = 0; i < count; i++) {
            List<ProfileContainer> children = new LinkedList<>(getSortedChildren());
            Collections.reverse(children);
            for (ProfileContainer child : children) {
                if (child.hasProfile(profile)) {
                    child.removeProfile(profile);
                    break;
                }
            }
        }
    }

    public int getProfileCount() {
        int count = 0;
        for (ProfileContainer child : childMap.values()) {
            count += child.getProfileCount();
        }
        return count;
    }

    final public int getProfileCount(Profile profile) {
        return getProfileCount(profile.getId());
    }

    final public int getProfileCount(ProfileRequirements profile) {
        return getProfileCount(profile.getProfile());
    }

    public int getProfileCount(String profileId) {
        int count = 0;
        for (ProfileContainer child : childMap.values()) {
            count += child.getProfileCount(profileId);
        }
        return count;
    }

    final public String getId() {
        return id;
    }

    public void remove() {
        removed = true;
    }

    final public boolean isRemovable() {
        return removable && !removed;
    }

    final public boolean isRemoved() {
        return removed;
    }

    final public List<ProfileContainer> getChildren() {
        List<ProfileContainer> result = new ArrayList<>();
        for (ProfileContainer child : childMap.values()) {
            if (!child.isRemoved()) {
                result.add(child);
            }
        }
        return result;
    }

    final public List<ProfileContainer> getSortedChildren() {
        List<ProfileContainer> result = new LinkedList<>(childMap.values());
        Collections.sort(result, childComparator);
        return result;
    }

    final public List<ProfileContainer> getRemovableChildren() {
        List<ProfileContainer> result = new ArrayList<>();
        for (ProfileContainer child : childMap.values()) {
            if (child.isRemovable()) {
                result.add(child);
            }
        }
        return result;
    }

    final public void removeChild(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            List<ProfileContainer> removables = new LinkedList<>(getRemovableChildren());
            if (!removables.isEmpty()) {
                Collections.sort(removables, childComparator);
                removables.get(0).remove();
                LOGGER.debug("Marked container {} for removal", removables.get(0).getId());
            } else {
                throw new Exception("No more removable children available for " + id + " (removal of " + (count - i) + " requested");
            }
        }
    }

    public void removeProfiles(long count) {
        for (int i = 0; i < count; i++) {
            List<ProfileContainer> children = new LinkedList<>(getSortedChildren());
            children.get(children.size()).removeProfiles(1);
        }
    }

    public static class SortByProfileCount implements Comparator<ProfileContainer> {
        @Override
        public int compare(ProfileContainer container, ProfileContainer t1) {
            return container.getProfileCount() - t1.getProfileCount();
        }
    }

    public static class SortByContainerCount implements Comparator<ProfileContainer> {
        @Override
        public int compare(ProfileContainer container, ProfileContainer t1) {
            return container.getChildren().size() - t1.getChildren().size();
        }
    }
}
