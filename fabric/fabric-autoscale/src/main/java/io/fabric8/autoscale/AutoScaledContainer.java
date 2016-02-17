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
import io.fabric8.api.ProfileRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoScaledContainer extends ProfileContainer implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoScaledContainer.class);

    private final Container container;
    private final Map<String, AutoScaledHost> hostMap;
    private final Map<Profile, Boolean> profiles = new HashMap<>();
    private final Matcher profilePattern;
    private final AutoScaledGroup group;

    private AutoScaledHost host;
    private Boolean remove = false;

    private AutoScaledContainer(Container container, String id, Matcher profilePattern, Map<String, AutoScaledHost> hostMap, AutoScaledGroup group) {
        this.container = container;
        this.id = id;
        this.profilePattern = profilePattern;
        this.hostMap = hostMap;
        this.group = group;

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
                        profiles.put(profile, true);
                    } catch (Exception e) {
                        LOGGER.error("Couldn't add profile {} to container {}", profile.getId(), id);
                    }
                }
            }
        }
    }

    public static AutoScaledContainer newAutoScaledContainer(AutoScaledGroup group, Container container) {
        return new AutoScaledContainer(container, container.getId(), group.getProfilePattern(), group.getHostMap(), group);
    }

    public static AutoScaledContainer newAutoScaledContainer(AutoScaledGroup group, String id) {
        return new AutoScaledContainer(null, id, group.getProfilePattern(), group.getHostMap(), group);
    }

    private void setHost(AutoScaledHost host) {
        this.host = host;
        hostMap.put(host.getId(), host);
        host.addProfileContainer(this);
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
    public void removeProfile(String profile, int count) {
        profiles.put(container.getVersion().getProfile(profile), false); // Ignore count
    }

    public void removeProfiles(long count) {
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
    public void addProfile(ProfileRequirements profile, int count) throws Exception {
        if (getProfileCount() + count > group.getMaxAssignmentsPerContainer()) {
            throw new Exception("Can't assign " + profile.getProfile() + " to container " + id + ", due to maxInstancesPerContainer (" + group.getMaxAssignmentsPerContainer() + ").");
        } else if (profile.getMaximumInstancesPerHost() != null && host.getProfileCount(profile) + count > profile.getMaximumInstancesPerHost()) {
            throw new Exception("Can't assign " + profile.getProfile() + " to container " + id + ", due to maxInstancesPerHost (" + profile.getMaximumInstancesPerHost() + ").");
        } else if (profile.getMaximumInstances() != null && group.getProfileCount(profile) + count > profile.getMaximumInstances()) {
            throw new Exception("Can't assign " + profile.getProfile() + " to container " + id + ", due to maxInstances (" + profile.getMaximumInstances() + ").");
        } else {
            for (int i = 0; i < count; i++) {
                profiles.put(container.getVersion().getProfile(profile.getProfile()), true);
            }
        }
    }

    @Override
    public int getProfileCount(String profileId) {
        if (profiles.containsKey(container.getVersion().getProfile(profileId))) {
            return 1;
        } else {
            return 0;
        }
    }

    public void remove() {
        this.remove = true;
        host.removeProfileContainer(this);
        profiles.clear();
    }

    public ProfileContainer getHost() {
        return host;
    }

    @Override
    public void run() {
        if (remove) {
            // Remove container
            container.destroy(true);
            return;
        }

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
}
