package io.fabric8.autoscale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import io.fabric8.api.Container;
import io.fabric8.api.ProfileRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoScaledGroup extends ProfileContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoScaledGroup.class);

    private final Map<String, AutoScaledHost> hostMap = new HashMap<>();
    private final List<AutoScaledContainer> containerList = new LinkedList<>();
    private final Map<String, ProfileRequirements> profileRequirementsMap = new HashMap<>();
    private final AutoScaledGroupOptions options;
    private final Long maxAssignmentsPerContainer;

    public AutoScaledGroup(final String groupId, final AutoScaledGroupOptions options, final Container[] containers, final ProfileRequirements[] profileRequirements) throws Exception {
        this.id = groupId;
        this.options = options;

        // Collect all applicable profile requirements
        int profileInstancesTotal = 0; // Total number of required profile instances
        int requiredHosts = 0; // Number of hosts needed to satisfy the requirements
        for (ProfileRequirements profile : checkProfileRequirements(profileRequirements, options.getProfilePattern(), options.getInheritRequirements())) {
            if (profile.getMaximumInstancesPerHost() == null) {
                profile.setMaximumInstancesPerHost(options.getDefaultMaximumInstancesPerHost());
            }
            profileRequirementsMap.put(profile.getProfile(), profile);
            if (profile.hasMinimumInstances() && profile.getMaximumInstancesPerHost() > 0) {
                requiredHosts = Math.max(requiredHosts, (profile.getMinimumInstances() + profile.getMaximumInstancesPerHost() - 1) / profile.getMaximumInstancesPerHost());
            }
            if (profile.hasMinimumInstances()) {
                profileInstancesTotal += profile.getMinimumInstances();
            } else {
                profileInstancesTotal++; // These are dependencies without minimum instances set
            }
        }

        // Collect root container hosts
        for (Container container : containers) {
            if (container.isRoot()) {
                AutoScaledHost rootHost = new AutoScaledHost(container.getIp(), container);
                hostMap.put(rootHost.getId(), rootHost);
            }
        }

        // Collect all applicable containers
        if (options.getScaleContainers()) {
            Set<String> collectedHosts = new HashSet<>();
            for (Container container : containers) {
                if (options.getContainerPattern().reset(container.getId()).matches()) {
                    AutoScaledContainer autoScaledContainer = AutoScaledContainer.newAutoScaledContainer(this, container);
                    containerList.add(autoScaledContainer);
                    collectedHosts.add(container.getIp());
                }
            }
            // Scale containers
            if (options.getAverageAssignmentsPerContainer() < 1) {
                throw new Exception("averageAssignmentsPerContainer < 1");
            }
            int desiredContainerCount = (profileInstancesTotal + options.getAverageAssignmentsPerContainer() - 1) / options.getAverageAssignmentsPerContainer(); // Ceiling
            if (desiredContainerCount < requiredHosts) {
                desiredContainerCount = requiredHosts;
            }
            adjustContainerCount(desiredContainerCount - containerList.size(), requiredHosts - collectedHosts.size());
        } else {
            for (Container container : containers) {
                if (options.getContainerPattern().reset(container.getId()).matches() && container.isAlive()) {
                    AutoScaledContainer autoScaledContainer = AutoScaledContainer.newAutoScaledContainer(this, container);
                    containerList.add(autoScaledContainer);
                }
            }
            if (containerList.size() < options.getMinContainerCount()) {
                if (options.getIgnoreErrors()) {
                    LOGGER.error("Not enough containers (" + containerList.size() + "), " + options.getMinContainerCount() + " required");
                } else {
                    throw new Exception("Not enough containers (" + containerList.size() + "), " + options.getMinContainerCount() + " required");
                }
            }
        }

        // Calculate max profile instances per container
        this.maxAssignmentsPerContainer = calculateMaxAssignmentsPerContainer(containerList.size(), profileInstancesTotal, options.getAverageAssignmentsPerContainer(), options.getMaxDeviation());

        // Apply profile requirements on the containers
        applyProfileRequirements();
    }

    private void adjustContainerCount(int containerDelta, int hostDelta) throws Exception {
        if (containerDelta > 0) {
            // Add containers
            for (int i = 0; i < containerDelta; i++) {
                try {
                    String containerId = createContainerId();
                    AutoScaledContainer container = AutoScaledContainer.newAutoScaledContainer(this, containerId, i < hostDelta);
                    containerList.add(container);
                } catch (Exception e) {
                    LOGGER.error("Failed to create new auto-scaled container.", e);
                    if (!options.getIgnoreErrors()) {
                        throw e;
                    }
                }
            }
        } else if (containerDelta < 0) {
            // Remove containers
            Collections.sort(containerList, new SortProfileContainers());
            for (AutoScaledContainer container : containerList.subList(0, -containerDelta)) {
                container.remove();
            }
        }
    }

    private String createContainerId() throws Exception {
        if (options.getContainerPattern().reset(options.getContainerPrefix()).matches()) {
            List<String> containerNames = new ArrayList<>();
            for (AutoScaledContainer container : containerList) {
                containerNames.add(container.getId());
            }
            for (int i = 0; i <= containerList.size(); i++) {
                if (!containerNames.contains(options.getContainerPrefix() + (i + 1))) {
                    return options.getContainerPrefix() + (i + 1);
                }
            }
        } else {
            throw new Exception("Container prefix doesn't match the container pattern.");
        }
        throw new Exception("Couldn't determine new container ID. This should never happen.");
    }

    private void applyProfileRequirements() throws Exception {
        adjustWithMaxInstancesPerContainer();
        adjustWithMaxInstancesPerHost();
        adjustWithMaxInstances();
        adjustWithMinInstances();
    }

    private void adjustWithMaxInstancesPerContainer() {
        for (AutoScaledContainer container : containerList) {
            long delta = container.getProfileCount() - maxAssignmentsPerContainer;
            if (delta > 0) {
                container.removeProfiles(delta);
            }
        }
    }

    private void adjustWithMaxInstancesPerHost() {
        for (ProfileRequirements profile : profileRequirementsMap.values()) {
            int maxInstancesPerHost = profile.getMaximumInstancesPerHost();
            for (AutoScaledHost host : hostMap.values()) {
                if (host.getProfileCount(profile) > maxInstancesPerHost) {
                    host.removeProfile(profile, host.getProfileCount(profile) - maxInstancesPerHost);
                }
            }
        }
    }

    private void adjustWithMaxInstances() {
        for (ProfileRequirements profile : profileRequirementsMap.values()) {
            if (profile.getMaximumInstances() != null) {
                int maxInstances = profile.getMaximumInstances();
                int delta = getProfileCount(profile) - maxInstances;
                if (delta > 0) {
                    removeProfile(profile, delta);
                }
            }
        }
    }

    private void adjustWithMinInstances() throws Exception {
        for (ProfileRequirements profile : profileRequirementsMap.values()) {
            if (profile.hasMinimumInstances()) {
                int delta = profile.getMinimumInstances() - getProfileCount(profile);
                if (delta > 0) {
                    try {
                        addProfile(profile, delta);
                    } catch (Exception e) {
                        LOGGER.error("Couldn't assign {} instances for profile {}", delta, profile.getProfile(), e);
                        if (!options.getIgnoreErrors()) {
                            throw e;
                        }
                    }
                }
            }
        }
    }

    // Check the profile requirements against profile pattern and check the profile dependencies
    private static List<ProfileRequirements> checkProfileRequirements(final ProfileRequirements[] profileRequirements, final Matcher profilePattern, final Boolean inheritRequirements) {
        Map<String, ProfileRequirements> profileRequirementsMap = new HashMap<>();
        for (ProfileRequirements p : profileRequirements) {
            profileRequirementsMap.put(p.getProfile(), p);
        }
        Map<String, ProfileRequirements> checkedProfileRequirements = new HashMap<>();
        for (ProfileRequirements p : profileRequirements) {
            if (p.hasMinimumInstances()) {
                // Skip root requirements without minimum instances
                checkProfileRequirements(p, checkedProfileRequirements, profileRequirementsMap, profilePattern, inheritRequirements);
            }
        }
        return new ArrayList<>(checkedProfileRequirements.values());
    }

    private static Map<String, ProfileRequirements> checkProfileRequirements(final ProfileRequirements parent, final Map<String, ProfileRequirements> checkedProfileRequirements, final Map<String, ProfileRequirements> profileRequirementsMap, final Matcher profilePattern, final Boolean inheritRequirements) {
        if (parent == null || !profilePattern.reset(parent.getProfile()).matches()) {
            // At the end or profile doesn't match the profile pattern
            return checkedProfileRequirements;
        }
        // Add this profile requirement to the result
        checkedProfileRequirements.put(parent.getProfile(), parent);
        if (parent.getDependentProfiles() == null) {
            // Profile doesn't have dependencies
            return checkedProfileRequirements;
        }
        if (!parent.hasMinimumInstances()) {
            // Profile doesn't have instances, skip the dependencies
            return checkedProfileRequirements;
        }
        // Check the profile dependencies
        for (String profile : parent.getDependentProfiles()) {
            if (!profilePattern.reset(profile).matches()) {
                // Profile dependency doesn't match profile pattern
                LOGGER.error("Profile dependency {} for profile {} doesn't match profile pattern.", profile, parent.getProfile());
                continue;
            }
            ProfileRequirements dependency = profileRequirementsMap.get(profile);
            if (inheritRequirements) {
                if (dependency == null) {
                    // Requirements missing, inherit them from the parent
                    dependency = new ProfileRequirements(profile, parent.getMinimumInstances(), parent.getMaximumInstances());
                } else if (!dependency.hasMinimumInstances()) {
                    // No instances for the dependency, inherit them from the parent
                    dependency.setMinimumInstances(parent.getMinimumInstances());
                    if (dependency.getMaximumInstances() != null && dependency.getMaximumInstances() < dependency.getMinimumInstances()) {
                        dependency.setMaximumInstances(parent.getMaximumInstances());
                    }
                }
            } else {
                if (dependency == null) {
                    // Requirements missing.
                    LOGGER.error("Profile dependency {} for profile {} is missing requirements.", profile, parent.getProfile());
                    continue;
                } else if (!dependency.hasMinimumInstances()) {
                    // No instances for the dependency.
                    LOGGER.error("Profile dependency {} for profile {} has no instances.", profile, parent.getProfile());
                    continue;
                }
            }
            checkProfileRequirements(dependency, checkedProfileRequirements, profileRequirementsMap, profilePattern, inheritRequirements);
        }
        return checkedProfileRequirements;
    }

    // Return the preferred maximum profile assignment count for a single container
    private static long calculateMaxAssignmentsPerContainer(int containers, int profileInstances, int desiredAveragePerContainer, double maxDeviation) {
        long average = desiredAveragePerContainer;
        if (desiredAveragePerContainer < 0 && containers > 0) {
            average = (profileInstances + containers - 1) / containers; // Ceiling of average
        } else if (desiredAveragePerContainer < 0) {
            average = 0;
        }
        return average + (int)Math.round(Math.abs(maxDeviation) * average);
    }

    @Override
    public boolean hasProfile(String profileId) {
        for (AutoScaledContainer container : containerList) {
            if (container.hasProfile(profileId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addProfile(ProfileRequirements profile, int count) throws Exception {
        Exception exception = null;
        count: for (int i = 0; i < count; i++) {
            Collections.sort(containerList, new SortProfileContainers());
            for (ProfileContainer container : containerList) {
                try {
                    container.addProfile(profile);
                    continue count;
                } catch (Exception e) {
                    exception = e;
                }
            }
            if (exception != null) {
                throw new Exception("Couldn't add profile " + profile.getProfile() + " to group " + id, exception);
            }
        }
    }

    @Override
    public void removeProfile(String profile, int count) {
        for (int i = 0; i < count; i++) {
            Collections.sort(containerList, new SortProfileContainers());
            Collections.reverse(containerList);
            for (ProfileContainer container : containerList) {
                if (container.hasProfile(profile)) {
                    container.removeProfile(profile);
                    break;
                }
            }
        }
    }

    @Override
    public int getProfileCount() {
        int count = 0;
        for (ProfileContainer container : containerList) {
            count += container.getProfileCount();
        }
        return count;
    }

    @Override
    public int getProfileCount(String profileId) {
        int count = 0;
        for (ProfileContainer container : containerList) {
            count += container.getProfileCount(profileId);
        }
        return count;
    }

    public Matcher getProfilePattern() {
        return options.getProfilePattern();
    }

    public Map<String, AutoScaledHost> getHostMap() {
        return hostMap;
    }

    public long getMaxAssignmentsPerContainer() {
        return maxAssignmentsPerContainer;
    }

    private class SortProfileContainers implements Comparator<ProfileContainer> {
        @Override
        public int compare(ProfileContainer container, ProfileContainer t1) {
            return container.getProfileCount() - t1.getProfileCount();
        }
    }

    public void apply() {
        ExecutorService taskExecutor = Executors.newFixedThreadPool(containerList.size());
        for (AutoScaledContainer container : containerList) {
            taskExecutor.execute(container);
        }
        taskExecutor.shutdown();
    }

    public void applyAndWait(long maxWait) {
        ExecutorService taskExecutor = Executors.newFixedThreadPool(containerList.size());
        for (AutoScaledContainer container : containerList) {
            taskExecutor.execute(container);
        }
        taskExecutor.shutdown();
        try {
            taskExecutor.awaitTermination(maxWait, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace(); // ignored
        }
    }

    public List<AutoScaledContainer> getContainers() {
        return containerList;
    }

    public List<AutoScaledHost> getHosts() {
        return new ArrayList<>(hostMap.values());
    }

    public AutoScaledGroupOptions getOptions() {
        return options;
    }
}
