package io.fabric8.autoscale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import io.fabric8.api.Container;
import io.fabric8.api.FabricService;
import io.fabric8.api.ProfileRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoScaledCluster extends ProfileContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoScaledCluster.class);

    private final Map<String, AutoScaledHost> hostMap = new HashMap<>();
    private final Map<String, AutoScaledContainer> containerMap = new HashMap<>();
    private final Map<String, ProfileRequirements> profileRequirementsMap = new HashMap<>();
    private final Matcher containerPattern;
    private final Matcher profilePattern;
    private final Boolean scaleContainers;
    private final Boolean inheritRequirements;
    private final Double maxDeviation;
    private final Integer averageAssignmentsPerContainer;
    private final String containerPrefix;
    private final Integer maxInstancesPerContainer;

    public AutoScaledCluster(
        FabricService service,
        Matcher containerPattern,
        Matcher profilePattern,
        Boolean scaleContainers,
        Boolean inheritRequirements,
        Double maxDeviation,
        Integer averageAssignmentsPerContainer,
        String containerPrefix) {
        this.containerPattern = containerPattern;
        this.profilePattern = profilePattern;
        this.scaleContainers = scaleContainers;
        this.inheritRequirements = inheritRequirements;
        this.maxDeviation = maxDeviation;
        this.averageAssignmentsPerContainer = averageAssignmentsPerContainer;
        this.containerPrefix = containerPrefix;

        // Collect all applicable containers
        for (Container container : Arrays.asList(service.getContainers())) {
            if (containerPattern.reset(container.getId()).matches() && container.isAlive()) {
                AutoScaledContainer autoScaledContainer = AutoScaledContainer.newAutoScaledContainer(this, container);
                containerMap.put(autoScaledContainer.getId(), autoScaledContainer);
            }
        }

        // Collect all applicable profile requirements
        for (ProfileRequirements p : checkProfileRequirements(service.getRequirements().getProfileRequirements(), profilePattern, inheritRequirements)) {
            profileRequirementsMap.put(p.getProfile(), p);
        }

        // Calculate max instances per container
        this.maxInstancesPerContainer = getMaxAssignmentsPerContainer();

        if (scaleContainers) {
            // Scale containers
            int desiredContainerCount = (int)Math.ceil(profileRequirementsMap.size() / containerMap.size());
            adjustContainerCount(containerMap.size() - desiredContainerCount);
        }

        // Apply profile requirements on the containers
        applyProfileRequirements();
    }

    private void adjustContainerCount(int delta) {
        if (delta > 0) {
            // Remove containers
            List<AutoScaledContainer> containers = new LinkedList<>(containerMap.values());
            Collections.sort(containers, new SortAutoscaledContainersByProfileCount());
            for (AutoScaledContainer container : containers.subList(0, delta)) {
                container.remove();
            }
        } else if (delta < 0) {
            // Add containers
            for (int i = delta; i < 0; i++) {
                try {
                    String containerId = createContainerId();
                    AutoScaledContainer container = AutoScaledContainer.newAutoScaledContainer(this, containerId);
                    containerMap.put(container.getId(), container);
                } catch (Exception e) {
                    LOGGER.error("Failed to create new auto-scaled container.", e);
                }
            }
        }
    }

    private String createContainerId() throws Exception {
        if (containerPattern.reset(containerPrefix).matches()) {
            for (int i = 0; i <= containerMap.size(); i++) {
                if (!containerMap.containsKey(containerPrefix + (i + 1))) {
                    return containerPrefix + (i + 1);
                }
            }
        } else {
            throw new Exception("Container prefix doesn't match the container pattern.");
        }
        throw new Exception("Couldn't determine new container ID. This should never happen.");
    }

    private void applyProfileRequirements() {
        adjustWithMaxInstancesPerContainer();
        adjustWithMaxInstancesPerHost();
        adjustWithMaxInstances();
        adjustWithMinInstances();
    }

    private void adjustWithMaxInstancesPerContainer() {
        for (AutoScaledContainer container : containerMap.values()) {
            int delta = container.getProfileCount() - maxInstancesPerContainer;
            if (delta > 0) {
                container.removeProfiles(delta);
            }
        }
    }

    private void adjustWithMaxInstancesPerHost() {
        for (ProfileRequirements p : profileRequirementsMap.values()) {
            String profileId = p.getProfile();
            int maxInstancesPerHost = p.getMaximumInstancesPerHost();
            for (AutoScaledHost host : hostMap.values()) {
                if (host.getProfileCount(profileId) > maxInstancesPerHost) {
                    host.removeProfile(profileId, host.getProfileCount(profileId) - maxInstancesPerHost);
                }
            }
        }
    }

    private void adjustWithMaxInstances() {
        for (ProfileRequirements p : profileRequirementsMap.values()) {
            String profileId = p.getProfile();
            int maxInstances = p.getMaximumInstances();
            int delta = getProfileCount(profileId) - maxInstances;
            if (delta > 0) {
                removeProfile(profileId, delta);
            }
        }
    }

    private void adjustWithMinInstances() {
        for (ProfileRequirements p : profileRequirementsMap.values()) {
            String profileId = p.getProfile();
            int minInstances = p.getMinimumInstances();
            int delta = minInstances - getProfileCount(profileId);
            if (delta > 0) {
                try {
                    addProfile(profileId, delta);
                } catch (Exception e) {
                    LOGGER.error("Couldn't assign {} instances for profile {}", delta, profileId, e);
                }
            }
        }
    }

    // Check the profile requirements against profile pattern and check the profile dependencies
    private static List<ProfileRequirements> checkProfileRequirements(Collection<ProfileRequirements> profileRequirements, Matcher profilePattern, Boolean inheritRequirements) {
        Map<String, ProfileRequirements> profileRequirementsMap = new HashMap<>();
        for (ProfileRequirements p : profileRequirements) {
            profileRequirementsMap.put(p.getProfile(), p);
        }
        Map<String, ProfileRequirements> checkedProfileRequirements = new HashMap<>();
        for (ProfileRequirements p : profileRequirements) {
            checkProfileRequirements(p, checkedProfileRequirements, profileRequirementsMap, profilePattern, inheritRequirements);
        }
        return new ArrayList<>(checkedProfileRequirements.values());
    }

    private static Map<String, ProfileRequirements> checkProfileRequirements(ProfileRequirements parent, Map<String, ProfileRequirements> checkedProfileRequirements, Map<String, ProfileRequirements> profileRequirementsMap, Matcher profilePattern, Boolean inheritRequirements) {
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
    private int getMaxAssignmentsPerContainer() {
        int average = averageAssignmentsPerContainer;
        if (averageAssignmentsPerContainer < 0) {
            average = (int) Math.ceil(profileRequirementsMap.size() / containerMap.size());
        }
        return average + (int)Math.round(Math.abs(maxDeviation) * average);
    }

    @Override
    public boolean hasProfile(String profileId) {
        for (AutoScaledContainer container : containerMap.values()) {
            if (container.hasProfile(profileId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addProfile(String profileId, int count) throws Exception {

    }

    @Override
    public void removeProfile(String profile, int count) {

    }

    @Override
    public int getProfileCount() {
        return 0;
    }

    @Override
    public int getProfileCount(String profileId) {
        return 0;
    }

    public Matcher getProfilePattern() {
        return profilePattern;
    }

    public Map<String, AutoScaledHost> getHostMap() {
        return hostMap;
    }

    private class SortAutoscaledContainersByProfileCount implements Comparator<AutoScaledContainer> {
        @Override
        public int compare(AutoScaledContainer autoScaledContainer, AutoScaledContainer t1) {
            return autoScaledContainer.getProfileCount() - t1.getProfileCount();
        }
    }
}
