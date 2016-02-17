package io.fabric8.autoscale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
    private final Map<String, Boolean> profiles = new HashMap<>();
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
                        profiles.put(profile.getId(), true);
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
        profiles.put(profile, false); // Ignore count
    }

    public void removeProfiles(long count) {
        Iterator<String> iterator = profiles.keySet().iterator();
        for (int i = 0; i < count && iterator.hasNext(); i++) {
            iterator.remove();
        }
    }

    @Override
    public boolean hasProfile(String profileId) {
        if (profiles.containsKey(profileId)) {
            return profiles.get(profileId);
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
                profiles.put(profile.getProfile(), true);
            }
        }
    }

    @Override
    public int getProfileCount(String profileId) {
        if (profiles.containsKey(profileId)) {
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

        // Get current profiles for the container
        final Set<String> currentProfiles = new HashSet<>();
        if (container != null) {
            for (Profile profile : container.getProfiles()) {
                currentProfiles.add(profile.getId());
            }
        }

        // Clean up matching profiles that have no requirements
        for (String profile : currentProfiles) {
            if (profilePattern.reset(profile).matches() && !hasProfile(profile)) {
                this.removeProfile(profile);
            }
        }

        // Find the changes
        final List<String> resultProfiles = new LinkedList<>(currentProfiles);
        for (Map.Entry<String, Boolean> entry : profiles.entrySet()) {
            final String profile = entry.getKey();
            final Boolean assigned = entry.getValue();
            if (assigned) {
                resultProfiles.add(profile);
            } else {
                resultProfiles.remove(profile);
            }
        }

        // Apply possible changes
        if (!resultProfiles.equals(currentProfiles)) {
            Collections.sort(resultProfiles);
            if (container != null) {
                List<Profile> profiles = new ArrayList<>();
                for (String profileId : resultProfiles) {
                    profiles.add(container.getVersion().getProfile(profileId));
                }
                // Adjust existing container
                LOGGER.info("Setting profiles for container {}", container.getId());
                container.setProfiles(profiles.toArray(new Profile[profiles.size()]));
            } else {
                // Create container
                // TODO: 14.2.2016 create a new container and apply the profiles on it
            }
        }
    }
}
