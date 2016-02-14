package io.fabric8.autoscale;

import io.fabric8.api.Profile;

public interface ProfileContainer {
    boolean hasProfile(Profile profile);

    boolean hasProfile(String profileId);

    void addProfile(Profile profile) throws Exception;

    void addProfile(String profileId) throws Exception;

    void removeProfile(Profile profile);

    void removeProfile(String profileId);

    void removeProfile(Profile profile, int count);

    void removeProfile(String profile, int count);

    int getProfileCount();

    int getProfileCount(Profile profile);

    int getProfileCount(String profileId);

    String getId();
}
