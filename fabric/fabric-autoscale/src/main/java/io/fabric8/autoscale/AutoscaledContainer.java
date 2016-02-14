package io.fabric8.autoscale;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;

import io.fabric8.api.Container;
import io.fabric8.api.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoscaledContainer implements Runnable, ProfileContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoScaleController.class);

    private final Container container;
    private final String containerId;
    private final Map<String, AutoscaledHost> hostMap;
    private final Map<Profile, Boolean> profiles = new HashMap<>();
    private final Matcher profilePattern;

    private AutoscaledHost host;

    public AutoscaledContainer(Container container, String containerId, Matcher profilePattern, Map<String, AutoscaledHost> hostMap) {
        this.container = container;
        this.containerId = containerId;
        this.profilePattern = profilePattern;
        this.hostMap = hostMap;

        String hostId = UUID.randomUUID().toString();
        if (container != null) {
            // Existing container
            hostId = container.getIp();
        } else {
            // New container
            // TODO: 14.2.2016 get host ID for new container if possible
        }
        setHost(hostId);

        // Collect current profiles
        if (container != null) {
            for (Profile profile : Arrays.asList(container.getProfiles())) {
                if (profilePattern.reset(profile.getId()).matches()) {
                    addProfile(profile);
                }
            }
        }
    }

    public AutoscaledContainer(String containerId, Matcher profilePattern, Map<String, AutoscaledHost> hostMap) {
        this(null, containerId, profilePattern, hostMap);
    }

    private void setHost(AutoscaledHost host) {
        this.host = host;
        hostMap.put(host.getId(), host);
        host.addAutoscaledContainer(this);
    }

    private void setHost(String hostId) {
        if (hostMap.containsKey(hostId)) {
            setHost(hostMap.get(hostId));
        } else {
            setHost(new AutoscaledHost(hostId));
        }
    }

    @Override
    public int getProfileCount() {
        int count = 0;
        for (Boolean assigned : profiles.values()) {
            if (assigned) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String getId() {
        return containerId;
    }

    @Override
    public void addProfile(Profile profile) {
        profiles.put(profile, true);
    }

    @Override
    public void addProfile(String profileId) {
        addProfile(container.getVersion().getProfile(profileId));
    }

    @Override
    public void removeProfile(Profile profile) {
        profiles.put(profile, false);
    }

    @Override
    public void removeProfile(String profileId) {
        removeProfile(container.getVersion().getProfile(profileId));
    }

    @Override
    public void removeProfile(Profile profile, int count) {
        removeProfile(profile); // Ignore count
    }

    @Override
    public void removeProfile(String profile, int count) {
        removeProfile(profile); // Ignore count
    }

    public void removeProfiles(int count) {
        Profile[] ps = profiles.keySet().toArray(new Profile[profiles.keySet().size()]);
        for (int i = 0; i < count; i++) {
            removeProfile(ps[i]);
        }
    }

    @Override
    public boolean hasProfile(Profile profile) {
        return profiles.containsKey(profile) && profiles.get(profile);
    }

    @Override
    public boolean hasProfile(String profileId) {
        return hasProfile(container.getVersion().getProfile(profileId));
    }

    @Override
    public int getProfileCount(Profile profile) {
        if (profiles.containsKey(profile) && profiles.get(profile)) {
            return 1;
        }
        return 0;
    }

    @Override
    public int getProfileCount(String profileId) {
        return getProfileCount(container.getVersion().getProfile(profileId));
    }

    @Override
    public void run() {
        final Set<Profile> currentProfiles = new HashSet<>(Arrays.asList(container.getProfiles()));
        final Set<Profile> resultProfiles = new HashSet<>(currentProfiles);
        for (Map.Entry<Profile, Boolean> entry : profiles.entrySet()) {
            final Profile profile = entry.getKey();
            final Boolean assigned = entry.getValue();
            if (assigned) {
                resultProfiles.add(profile);
            } else {
                resultProfiles.remove(profile);
            }
        }
        if (!resultProfiles.equals(currentProfiles)) {
            Profile[] sortedResult = resultProfiles.toArray(new Profile[resultProfiles.size()]);
            Arrays.sort(sortedResult, new Comparator<Profile>() {
                @Override
                public int compare(Profile profile, Profile t1) {
                    return profile.getId().compareToIgnoreCase(t1.getId());
                }
            });
            LOGGER.info("Setting profiles for container {}", container.getId());
            container.setProfiles(sortedResult);
        }
    }
}
