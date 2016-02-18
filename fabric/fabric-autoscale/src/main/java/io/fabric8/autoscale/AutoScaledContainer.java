package io.fabric8.autoscale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
    private final Boolean newHost;

    private AutoScaledHost host;
    private Boolean removed = false;

    private AutoScaledContainer(Container container, String id, Matcher profilePattern, Map<String, AutoScaledHost> hostMap, AutoScaledGroup group, boolean newHost) throws Exception {
        this.container = container;
        this.id = id;
        this.profilePattern = profilePattern;
        this.hostMap = hostMap;
        this.group = group;
        this.newHost = newHost;

        if (container != null) {
            // Existing container
            setHost(container.getIp(), getRootContainer(container));
        } else if (newHost) {
            // New container on a new host
            setHost(UUID.randomUUID().toString()); // Any unique value goes
        } else {
            // New (child) container on an existing host
            // TODO: 17.2.2016 only if container provider is child otherwise throw ex
            List<Container> rootContainers = new LinkedList<>();
            for (AutoScaledHost host : hostMap.values()) {
                if (host.hasRootContainer()) {
                    rootContainers.add(host.getRootContainer());
                }
            }
            Collections.sort(rootContainers, new SortRootContainers());
            if (rootContainers.get(0) != null) {
                setHost(rootContainers.get(0));
            } else {
                throw new Exception("Can't add a child container. No root containers available.");
            }
        }

        if (group.getOptions().getMaxContainersPerHost() > 0 && host.getContainerCount() > group.getOptions().getMaxContainersPerHost()) {
            remove(); // Schedule the container to be removed
        } else {
            // Collect current profiles
            if (container != null) {
                for (Profile profile : Arrays.asList(container.getProfiles())) {
                    if (profilePattern.reset(profile.getId()).matches()) {
                        profiles.put(profile.getId(), true);
                    }
                }
            }
        }
    }

    private Container getRootContainer(Container container) {
        if (container.isRoot()) {
            return container;
        } else {
            return container.getParent();
        }
    }

    public static AutoScaledContainer newAutoScaledContainer(AutoScaledGroup group, Container container) throws Exception {
        return new AutoScaledContainer(container, container.getId(), group.getProfilePattern(), group.getHostMap(), group, false);
    }

    public static AutoScaledContainer newAutoScaledContainer(AutoScaledGroup group, String id, boolean newHost) throws Exception {
        return new AutoScaledContainer(null, id, group.getProfilePattern(), group.getHostMap(), group, newHost);
    }

    private void setHost(AutoScaledHost host) {
        this.host = host;
        hostMap.put(host.getId(), host);
        host.addProfileContainer(this);
    }

    private void setHost(String hostId, Container rootContainer) {
        if (hostMap.containsKey(hostId)) {
            setHost(hostMap.get(hostId));
        } else {
            setHost(new AutoScaledHost(hostId, rootContainer));
        }
    }

    private void setHost(String hostId) {
        setHost(hostId, null);
    }

    private void setHost(Container rootContainer) {
        setHost(rootContainer.getIp(), rootContainer);
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

    @Override
    public void remove() {
        this.removed = true;
        host.removeProfileContainer(this);
        profiles.clear();
    }

    public ProfileContainer getHost() {
        return host;
    }

    @Override
    public void run() {
        if (container != null && removed) {
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

        // Find the differences
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
                if (!container.isAlive()) {
                    container.start(true);
                }
            } else {
                // Create container
                // TODO: 14.2.2016 create a new container and apply the profiles on it
            }
        }
    }

    private class SortRootContainers implements Comparator<Container> {
        @Override
        public int compare(Container container, Container t1) {
            return container.getChildren().length - t1.getChildren().length;
        }
    }
}
