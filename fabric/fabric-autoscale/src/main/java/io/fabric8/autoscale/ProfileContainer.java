package io.fabric8.autoscale;

import io.fabric8.api.Profile;

public abstract class ProfileContainer {

    protected String id = "default";

    final public boolean hasProfile(Profile profile) {
        return hasProfile(profile.getId());
    }

    public abstract boolean hasProfile(String profileId);

    final public void addProfile(Profile profile) throws Exception {
        addProfile(profile.getId(), 1);
    }

    final public void addProfile(String profileId) throws Exception {
        addProfile(profileId, 1);
    }

    final public void addProfile(Profile profile, int count) throws Exception {
        addProfile(profile.getId(), count);
    }

    public abstract void addProfile(String profileId, int count) throws Exception;

    final public void removeProfile(Profile profile) {
        removeProfile(profile.getId(), 1);
    }

    final public void removeProfile(String profileId) {
        removeProfile(profileId, 1);
    }

    final public void removeProfile(Profile profile, int count) {
        removeProfile(profile.getId(), 1);
    }

    public abstract void removeProfile(String profile, int count);

    public abstract int getProfileCount();

    final public int getProfileCount(Profile profile) {
        return getProfileCount(profile.getId());
    }

    public abstract int getProfileCount(String profileId);

    final public String getId() {
        return id;
    }
}
