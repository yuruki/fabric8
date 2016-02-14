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

public class AutoScaledContainer extends ProfileContainer implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoScaledContainer.class);

    private final Container container;
    private final String id;
    private final Map<String, AutoScaledHost> hostMap;
    private final Map<Profile, Boolean> profiles = new HashMap<>();
    private final Matcher profilePattern;

    private AutoScaledHost host;
    private Boolean remove = false;

    private AutoScaledContainer(Container container, String id, Matcher profilePattern, Map<String, AutoScaledHost> hostMap) {
        this.container = container;
        this.id = id;
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
                    try {
                        addProfile(profile);
                    } catch (Exception e) {
                        LOGGER.error("Couldn't add profile {} to container {}", profile.getId(), id);
                    }
                }
            }
        }
    }

    public static AutoScaledContainer newAutoScaledContainer(AutoScaledGroup group, Container container) {
        return new AutoScaledContainer(container, container.getId(), group.getProfilePattern(), group.getHostMap());
    }

    public static AutoScaledContainer newAutoScaledContainer(AutoScaledGroup group, String id) {
        return new AutoScaledContainer(null, id, group.getProfilePattern(), group.getHostMap());
    }

    private void setHost(AutoScaledHost host) {
        this.host = host;
        hostMap.put(host.getId(), host);
        host.addAutoScaledContainer(this);
    }

    private void setHost(String hostId) {
        if (hostMap.containsKey(hostId)) {
            setHost(hostMap.get(hostId));
        } else {
            setHost(new AutoScaledHost(hostId));
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
    public void addProfile(String profileId, int count) throws Exception {

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
    public boolean hasProfile(String profileId) {
        Profile profile = container.getVersion().getProfile(profileId);
        if (profiles.containsKey(profile)) {
            return profiles.get(profile);
        } else {
            return false;
        }
    }

    @Override
    public int getProfileCount(String profileId) {
        return getProfileCount(container.getVersion().getProfile(profileId));
    }

    @Override
    public void run() {
        final Set<Profile> currentProfiles = new HashSet<>();
        if (container != null) {
            currentProfiles.addAll(Arrays.asList(container.getProfiles()));
        }

        // Clean up matching profiles that have no requirements
        for (Profile profile : currentProfiles) {
            if (profilePattern.reset(profile.getId()).matches() && !hasProfile(profile)) {
                this.removeProfile(profile);
            }
        }

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
            if (container != null) {
                // Adjust existing container
                LOGGER.info("Setting profiles for container {}", container.getId());
                container.setProfiles(sortedResult);
            } else {
                // Create container
                // TODO: 14.2.2016 create a new container and apply the profiles on it
            }
        }
    }

    public void remove() {
        this.remove = true;
        host.removeAutoScaledContainer(this);
        profiles.clear();
    }
}
