package io.fabric8.autoscale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.api.Container;
import io.fabric8.api.ProfileRequirements;

public class TestScenario {

    private final AutoScaledGroup group;
    private final AutoScaledGroupOptions options;
    private final List<Container> containers;
    private final List<ProfileRequirements> profileRequirements;

    public TestScenario(AutoScaledGroupOptions options, ProfileRequirements[] profileRequirements, Container[] containers) throws Exception {
        this.containers = Arrays.asList(containers);
        this.profileRequirements = Arrays.asList(profileRequirements);
        this.options = options;
        this.group = new AutoScaledGroup("test", options, containers, profileRequirements);
        this.group.apply();
    }

    public static AutoScaledGroup createTestScenario(AutoScaledGroupOptions options, ProfileRequirements[] profileRequirements, Integer profiles, Integer[] containers) throws Exception {
        // Create version
        MockVersion version = new MockVersion("1.0");
        // Create profiles
        for (int i = 0; i < profiles; i++) {
            version.addProfile(new MockProfile(String.format("test%d-auto", i)));
        }
        // Create containers
        List<MockContainer> containerList = new ArrayList<>();
        AtomicInteger containerCount = new AtomicInteger(1);
        for (int hostId = 0; hostId < containers.length; hostId++) {
            int containersPerHost = containers[hostId];
            for (int i = 0; i < containersPerHost; i++) {
                MockContainer container = new MockContainer("auto" + containerCount.getAndIncrement(), true, String.format("%d", hostId));
                container.setVersion(version);
                containerList.add(container);
            }
        }
        TestScenario testScenario = new TestScenario(options, profileRequirements, containerList.toArray(new Container[containerList.size()]));
        return testScenario.getGroup();
    }

    public AutoScaledGroup getGroup() {
        return group;
    }
}
