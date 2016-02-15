package io.fabric8.autoscale;

import io.fabric8.api.Profile;
import io.fabric8.api.ProfileRequirements;

public abstract class ProfileContainer {

    protected String id = "default";

    final public boolean hasProfile(Profile profile) {
        return hasProfile(profile.getId());
    }

    final public boolean hasProfile(ProfileRequirements profile) {
        return hasProfile(profile.getProfile());
    }

    public abstract boolean hasProfile(String profileId);

    final public void addProfile(ProfileRequirements profile) throws Exception {
        addProfile(profile, 1);
    }

    public abstract void addProfile(ProfileRequirements profile, int count) throws Exception;

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

    public abstract void removeProfile(String profileId, int count);

    public abstract int getProfileCount();

    final public int getProfileCount(Profile profile) {
        return getProfileCount(profile.getId());
    }

    final public int getProfileCount(ProfileRequirements profile) {
        return getProfileCount(profile.getProfile());
    }

    public abstract int getProfileCount(String profileId);

    final public String getId() {
        return id;
    }
}
