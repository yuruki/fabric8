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

public class AutoScaledContainer extends ProfileContainer implements Runnable {

    private final Container container;
    private final Map<String, Boolean> profiles = new HashMap<>();
    private final Matcher profilePattern;
    private final AutoScaledGroup group;
    private final ContainerFactory containerFactory;

    private ProfileContainer host;

    private AutoScaledContainer(Container container, String id, AutoScaledGroup group, boolean newHost, ContainerFactory containerFactory) throws Exception {
        this.container = container;
        this.id = id;
        this.profilePattern = group.getProfilePattern();
        this.group = group;
        this.containerFactory = containerFactory;

        if (container != null) {
            // Existing container
            setHost(container.getIp(), getRootContainer(container));
        } else if (newHost) {
            // New container on a new host
            setHost(UUID.randomUUID().toString()); // Any unique value goes
        } else {
            // New (child) container on an existing host
            // TODO: 17.2.2016 only if container provider is child otherwise throw ex
            ProfileContainer rootHost = null;
            for (ProfileContainer host : group.getSortedChildren()) {
                if (((AutoScaledHost) host).hasRootContainer()) {
                    rootHost = host;
                    break;
                }
            }
            if (rootHost != null) {
                setHost(rootHost);
            } else {
                throw new Exception("Can't add a child container. No root containers available.");
            }
        }

        // Collect current profiles
        if (container != null) {
            for (Profile profile : Arrays.asList(container.getProfiles())) {
                if (profilePattern.reset(profile.getId()).matches()) {
                    profiles.put(profile.getId(), true);
                } else if (removable) {
                    removable = false;
                }
            }
        }

        if (removable && group.getOptions().getMaxContainersPerHost() > 0 && host.getChildren().size() > group.getOptions().getMaxContainersPerHost()) {
            LOGGER.debug("MaxContainersPerHost ({}) exceeded on host {} with container {}. Marking container for removal", group.getOptions().getMaxContainersPerHost(), host.getId(), id);
            remove(); // Schedule the container to be removed
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
        return new AutoScaledContainer(container, container.getId(), group, false, null);
    }

    public static AutoScaledContainer newAutoScaledContainer(AutoScaledGroup group, String id, boolean newHost, ContainerFactory containerFactory) throws Exception {
        return new AutoScaledContainer(null, id, group, newHost, containerFactory);
    }

    private void setHost(ProfileContainer host) {
        this.host = host;
        host.addChild(this);
        group.addChild(host);
    }

    private void setHost(String hostId, Container rootContainer) {
        if (group.childMap.containsKey(hostId)) {
            setHost(group.childMap.get(hostId));
        } else {
            setHost(new AutoScaledHost(hostId, rootContainer));
        }
    }

    private void setHost(String hostId) {
        setHost(hostId, null);
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
        return profiles.containsKey(profileId) && profiles.get(profileId);
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
                // TODO: generalize for any provider, null checks
                try {
                    containerFactory.createChildContainer(id, resultProfiles.toArray(new String[resultProfiles.size()]), ((AutoScaledHost) host).getRootContainer());
                } catch (Exception e) {
                    LOGGER.error("Couldn't create child container {}. This exception is ignored.", id, e);
                }
            }
        }
    }
}
