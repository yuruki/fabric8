package io.fabric8.autoscale;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.api.Container;
import io.fabric8.api.ProfileRequirements;

import static org.junit.Assert.*;

public class ProfileContainerTest {

    @org.junit.Before
    public void setUp() throws Exception {
        // Set up profiles
        MockProfile profile1 = new MockProfile("first-auto");
        MockProfile profile2 = new MockProfile("second-auto");
        MockProfile profile3 = new MockProfile("third-auto");
        MockProfile profile4 = new MockProfile("fourth-auto");
        MockProfile profile5 = new MockProfile("fifth-auto");

        // Set up versions
        MockVersion version = new MockVersion("1.0");
        version.addProfile(profile1);
        version.addProfile(profile2);
        version.addProfile(profile3);
        version.addProfile(profile4);
        version.addProfile(profile5);

        // Set up containers
        MockContainer container1 = new MockContainer("auto1", true, "1");
        container1.setVersion(version);
        MockContainer container2 = new MockContainer("auto2", true, "1");
        container2.setVersion(version);
        MockContainer container3 = new MockContainer("auto3", true, "1");
        container3.setVersion(version);
        List<Container> containerList = new ArrayList<>();
        containerList.add(container1);
        containerList.add(container2);
        containerList.add(container3);

        // Set up profile requirements
        List<ProfileRequirements> profileRequirements = new ArrayList<>();
        profileRequirements.add(new ProfileRequirements(profile1.getId())); // No requirements
        profileRequirements.add(new ProfileRequirements(profile2.getId()).minimumInstances(1)); // Minimum instances
        profileRequirements.add(new ProfileRequirements(profile3.getId()).minimumInstances(1).dependentProfiles(profile4.getId())); // Minimum instances with dependency
        profileRequirements.add(new ProfileRequirements(profile5.getId()).minimumInstances(5).maximumInstancesPerHost(3));

        // Set up testables
        AutoScaledGroup autoScaledGroup = new AutoScaledGroup(
            "test",
            containerList.toArray(new Container[containerList.size()]),
            profileRequirements,
            containerPattern,
            profilePattern,
            scaleContainers,
            inheritRequirements,
            maxDeviation,
            averageAssignmentsPerContainer,
            containerPrefix,
            minContainerCount,
            defaultMaximumInstancesPerHost);
    }

    @org.junit.After
    public void tearDown() throws Exception {

    }

    @org.junit.Test
    public void testHasProfile() throws Exception {

    }

    @org.junit.Test
    public void testHasProfile1() throws Exception {

    }

    @org.junit.Test
    public void testHasProfile2() throws Exception {

    }

    @org.junit.Test
    public void testAddProfile() throws Exception {

    }

    @org.junit.Test
    public void testAddProfile1() throws Exception {

    }

    @org.junit.Test
    public void testRemoveProfile() throws Exception {

    }

    @org.junit.Test
    public void testRemoveProfile1() throws Exception {

    }

    @org.junit.Test
    public void testRemoveProfile2() throws Exception {

    }

    @org.junit.Test
    public void testRemoveProfile3() throws Exception {

    }

    @org.junit.Test
    public void testRemoveProfile4() throws Exception {

    }

    @org.junit.Test
    public void testGetProfileCount() throws Exception {

    }

    @org.junit.Test
    public void testGetProfileCount1() throws Exception {

    }

    @org.junit.Test
    public void testGetProfileCount2() throws Exception {

    }

    @org.junit.Test
    public void testGetProfileCount3() throws Exception {

    }

    @org.junit.Test
    public void testGetId() throws Exception {

    }
}