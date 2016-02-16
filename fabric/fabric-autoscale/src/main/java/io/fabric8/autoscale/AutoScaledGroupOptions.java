package io.fabric8.autoscale;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoScaledGroupOptions {

    private Matcher containerPattern = Pattern.compile(".*").matcher("");
    public static final String CONTAINER_PATTERN_DEFAULT = ".*";
    private Matcher profilePattern = Pattern.compile(".*").matcher("");
    public static final String PROFILE_PATTERN_DEFAULT = ".*";
    private Boolean scaleContainers = true;
    public static final String SCALE_CONTAINERS_DEFAULT = "true";
    private Boolean inheritRequirements = false;
    public static final String INHERIT_REQUIREMENTS_DEFAULT = "false";
    private Double maxDeviation = 1.0;
    public static final String MAX_DEVIATION_DEFAULT = "1.0";
    private Integer averageAssignmentsPerContainer = -1;
    public static final String AVERAGE_ASSIGNMENTS_PER_CONTAINER_DEFAULT = "-1";
    private String containerPrefix = "autoscale";
    public static final String CONTAINER_PREFIX_DEFAULT = "auto";
    private Integer minContainerCount = 0;
    public static final String MIN_CONTAINER_COUNT_DEFAULT = "0";
    private Integer defaultMaximumInstancesPerHost = 1;
    public static final String DEFAULT_MAX_INSTANCES_PER_HOST_DEFAULT = "1";

    public AutoScaledGroupOptions(
        Matcher containerPattern,
        Matcher profilePattern,
        Boolean scaleContainers,
        Boolean inheritRequirements,
        Double maxDeviation,
        Integer averageAssignmentsPerContainer,
        String containerPrefix,
        Integer minContainerCount,
        Integer defaultMaximumInstancesPerHost) {
        this.containerPattern = containerPattern;
        this.profilePattern = profilePattern;
        this.scaleContainers = scaleContainers;
        this.inheritRequirements = inheritRequirements;
        this.maxDeviation = maxDeviation;
        this.averageAssignmentsPerContainer = averageAssignmentsPerContainer;
        this.containerPrefix = containerPrefix;
        this.minContainerCount = minContainerCount;
        this.defaultMaximumInstancesPerHost = defaultMaximumInstancesPerHost;
    }

    public AutoScaledGroupOptions containerPattern(Matcher containerPattern) {
        setContainerPattern(containerPattern);
        return this;
    }

    public AutoScaledGroupOptions profilePattern(Matcher profilePattern) {
        setProfilePattern(profilePattern);
        return this;
    }

    public AutoScaledGroupOptions scaleContainers(Boolean scaleContainers) {
        setScaleContainers(scaleContainers);
        return this;
    }

    public AutoScaledGroupOptions inheritRequirements(Boolean inheritRequirements) {
        setInheritRequirements(inheritRequirements);
        return this;
    }

    public AutoScaledGroupOptions maxDeviation(Double maxDeviation) {
        setMaxDeviation(maxDeviation);
        return this;
    }

    public AutoScaledGroupOptions averageAssignmentsPerContainer(Integer averageAssignmentsPerContainer) {
        setAverageAssignmentsPerContainer(averageAssignmentsPerContainer);
        return this;
    }

    public AutoScaledGroupOptions containerPrefix(String containerPrefix) {
        setContainerPrefix(containerPrefix);
        return this;
    }

    public AutoScaledGroupOptions minContainerCount(Integer minContainerCount) {
        setMinContainerCount(minContainerCount);
        return this;
    }

    public AutoScaledGroupOptions defaultMaximumInstancesPerHost(Integer defaultMaximumInstancesPerHost) {
        setDefaultMaximumInstancesPerHost(defaultMaximumInstancesPerHost);
        return this;
    }

    public Matcher getContainerPattern() {
        return containerPattern;
    }

    public void setContainerPattern(Matcher containerPattern) {
        this.containerPattern = containerPattern;
    }

    public Matcher getProfilePattern() {
        return profilePattern;
    }

    public void setProfilePattern(Matcher profilePattern) {
        this.profilePattern = profilePattern;
    }

    public Boolean getScaleContainers() {
        return scaleContainers;
    }

    public void setScaleContainers(Boolean scaleContainers) {
        this.scaleContainers = scaleContainers;
    }

    public Boolean getInheritRequirements() {
        return inheritRequirements;
    }

    public void setInheritRequirements(Boolean inheritRequirements) {
        this.inheritRequirements = inheritRequirements;
    }

    public Double getMaxDeviation() {
        return maxDeviation;
    }

    public void setMaxDeviation(Double maxDeviation) {
        this.maxDeviation = maxDeviation;
    }

    public Integer getAverageAssignmentsPerContainer() {
        return averageAssignmentsPerContainer;
    }

    public void setAverageAssignmentsPerContainer(Integer averageAssignmentsPerContainer) {
        this.averageAssignmentsPerContainer = averageAssignmentsPerContainer;
    }

    public String getContainerPrefix() {
        return containerPrefix;
    }

    public void setContainerPrefix(String containerPrefix) {
        this.containerPrefix = containerPrefix;
    }

    public Integer getMinContainerCount() {
        return minContainerCount;
    }

    public void setMinContainerCount(Integer minContainerCount) {
        this.minContainerCount = minContainerCount;
    }

    public Integer getDefaultMaximumInstancesPerHost() {
        return defaultMaximumInstancesPerHost;
    }

    public void setDefaultMaximumInstancesPerHost(Integer defaultMaximumInstancesPerHost) {
        this.defaultMaximumInstancesPerHost = defaultMaximumInstancesPerHost;
    }
}
