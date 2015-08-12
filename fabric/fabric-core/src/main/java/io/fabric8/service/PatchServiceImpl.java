/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.service;

import io.fabric8.api.*;
import io.fabric8.utils.Base64Encoder;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatchServiceImpl implements PatchService {

    private static final String PATCH_ID = "id";
    private static final String PATCH_DESCRIPTION = "description";
    private static final String PATCH_BUNDLES = "bundle";
    private static final String PATCH_REQUIREMENTS = "requirement";
    private static final String PATCH_FILES = "file";
    private static final String PATCH_COUNT = "count";
    private static final String PATCH_RANGE = "range";
    private static final String PATCH_URL = "url";


    private static final Logger LOGGER = LoggerFactory.getLogger(PatchServiceImpl.class);

    private final ProfileService profileService;
    private final FabricService fabricService;

    public PatchServiceImpl(FabricService fabricService) {
        this.profileService = fabricService.adapt(ProfileService.class);
        this.fabricService = fabricService;
    }

    @Override
    public void applyPatch(Version version, URL patch, String login, String password) {
        try {
            // Load patch
            URI uploadUri = fabricService.getMavenRepoUploadURI();
            List<PatchDescriptor> descriptors = new ArrayList<PatchDescriptor>();

            if(!isZipValid(patch.getFile())){
                throw new PatchException("Invalid zip file: " + patch.getFile());
            }

            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(patch.openStream()));
            try {
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        if (entryName.startsWith("repository/")) {
                            String fileName = entryName.substring("repository/".length());
                            while (fileName.startsWith("/")) {
                                fileName = fileName.substring(1);
                            }
                            URL uploadUrl = uploadUri.resolve(fileName).toURL();
                            URLConnection con = uploadUrl.openConnection();
                            if (con instanceof HttpURLConnection) {
                                ((HttpURLConnection) con).setRequestMethod("PUT");
                            }
                            if (login != null && password != null) {
                                con.setRequestProperty("Authorization", "Basic " + Base64Encoder.encode(login + ":" + password));
                            }
                            con.setDoInput(true);
                            con.setDoOutput(true);
                            con.connect();
                            OutputStream os = con.getOutputStream();
                            try {
                                try {
                                    copy(zis, os);
                                    if (con instanceof HttpURLConnection) {
                                        int code = ((HttpURLConnection) con).getResponseCode();
                                        if (code < 200 || code >= 300) {
                                            throw new IOException("Error uploading patched jars: " + ((HttpURLConnection) con).getResponseMessage());
                                        }
                                    }
                                } finally {
                                    zis.closeEntry();
                                }
                            } finally {
                                close(os);
                            }
                        } else if (entryName.endsWith(".patch") && !entryName.contains("/")) {
                            try {
                                Properties patchMetadata = new Properties();
                                patchMetadata.load(zis);
                                descriptors.add(new PatchDescriptor(patchMetadata));
                            } finally {
                                zis.closeEntry();
                            }
                        }
                    }
                    entry = zis.getNextEntry();
                }
            } finally {
                close(zis);
            }
            // Check if all required patches are available
            checkRequirements(version, descriptors);
            // Create patch profile
            List<Profile> profiles = version.getProfiles();
            for (PatchDescriptor descriptor : descriptors) {
                String profileId = "patch-" + descriptor.getId();
                Profile profile = getPatchProfile(version, descriptor);
                for (Profile p : profiles) {
                    if (profileId.equals(p.getId())) {
                        profile = p;
                        break;
                    }
                }
                if (profile == null) {
                    String versionId = version.getId();
                    ProfileBuilder builder = ProfileBuilder.Factory.create(versionId, profileId);
                    builder.setOverrides(descriptor.getBundles());
                    builder.setLibs(descriptor.getLibs());
                    profile = profileService.createProfile(builder.getProfile());
                    Profile defaultProfile = version.getRequiredProfile("default");
                    List<String> parentIds = new LinkedList<String>();
                    parentIds.addAll(defaultProfile.getParentIds());
                    if (!parentIds.contains(profile.getId())) {
                        parentIds.add(profile.getId());
                        builder = ProfileBuilder.Factory.createFrom(defaultProfile);
                        builder.setParents(parentIds);
                        profileService.updateProfile(builder.getProfile());
                    }
                } else {
                    LOGGER.info("The patch {} has already been applied to version {}, ignoring.", descriptor.getId(), version.getId());
                }
            }
        } catch (PatchException e) {
            // PatchException already is a RuntimeException - simply rethrowing it
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unable to apply patch", e);
        }
    }

    /**
     * Check if all required patches for a patch are available in the specified version
     * @throws io.fabric8.api.PatchException if a required patch is missing
     */
    protected static void checkRequirements(Version version, PatchDescriptor descriptor) {
        for (String requirement : descriptor.getRequirements()) {
            if (getPatchProfile(version, requirement) == null) {
                throw new PatchException(String.format("Unable to install patch '%s' - required patch '%s' is missing in version %s",
                                                       descriptor.getId(), requirement, version.getId()));
            }
        }
    }

    /**
     * Check if the requirements for all patches have been applied to the specified version
     * @throws java.lang.RuntimeException if a required patch is missing
     */
    protected static void checkRequirements(Version version, Collection<PatchDescriptor> patches) {
        for (PatchDescriptor patch : patches) {
            checkRequirements(version, patch);
        }
    }

    /**
     * Get the patch profile for a specified patch descriptor from a {@link Version}
     * Returns <code>null</code> if no matching profile was found
     */
    protected static Profile getPatchProfile(Version version, PatchDescriptor patch) {
        return getPatchProfile(version, patch.getId());
    }

    /**
     * Get the patch profile for a specified patch id from a {@link Version}
     * Returns <code>null</code> if no matching profile was found
     */
    protected static Profile getPatchProfile(Version version, String patchId) {
        return version.getProfile("patch-" + patchId);
    }

    static class PatchDescriptor {

        final String id;
        final String description;
        final List<String> bundles;
        final List<String> requirements;
        final List<String> libs;
        final List<String> endorsedLibs;
        final List<String> extLibs;

        PatchDescriptor(Properties properties) {
            this.id = properties.getProperty(PATCH_ID);
            this.description = properties.getProperty(PATCH_DESCRIPTION);
            // parse the bundle URLs and optionally, the bundle version ranges
            this.bundles = new ArrayList<String>();
            int count = Integer.parseInt(properties.getProperty(PATCH_BUNDLES + "." + PATCH_COUNT, "0"));
            for (int i = 0; i < count; i++) {
                String url = properties.getProperty(PATCH_BUNDLES + "." + Integer.toString(i));

                String range = properties.getProperty(PATCH_BUNDLES + "." + Integer.toString(i) + "." + PATCH_RANGE);
                if (range != null) {
                    url = String.format("%s;range=%s", url, range);
                }

                this.bundles.add(url);
            }

            // parse the requirements
            this.requirements = new LinkedList<String>();
            count = Integer.parseInt(properties.getProperty(PATCH_REQUIREMENTS + "." + PATCH_COUNT, "0"));
            for (int i = 0; i < count; i++) {
                String requirement = properties.getProperty(PATCH_REQUIREMENTS + "." + Integer.toString(i));
                this.requirements.add(requirement);
            }

            libs = new LinkedList<>();
            endorsedLibs = new LinkedList<>();
            extLibs = new LinkedList<>();
            count = Integer.parseInt(properties.getProperty(PATCH_FILES + "." + PATCH_COUNT, "0"));
            for (int i = 0; i < count; i++) {
                String value = properties.getProperty(PATCH_FILES + "." + i);
                String url = properties.getProperty(PATCH_FILES + "." + i + "." + PATCH_URL);
                if (url != null) {
                    if (value.startsWith("lib/endorsed/")) {
                        String name = value.replaceFirst("lib/endorsed/", "");
                        endorsedLibs.add(String.format("%s;filename=%s", url, name));
                    } else if (value.startsWith("lib/ext/")) {
                        String name = value.replaceFirst("lib/ext/", "");
                        extLibs.add(String.format("%s;filename=%s", url, name));
                    } else if (value.startsWith("lib/")) {
                        String name = value.replaceFirst("lib/", "");
                        libs.add(String.format("%s;filename=%s", url, name));
                    }
                }
            }
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getBundles() {
            return bundles;
        }

        public List<String> getRequirements() {
            return requirements;
        }

        public List<String> getLibs() {
            return libs;
        }

        public List<String> getEndorsedLibs() {
            return endorsedLibs;
        }

        public List<String> getExtLibs() {
            return extLibs;
        }


    }


        public static boolean isZipValid(String fileName) {
            boolean result = true;

            try {
                ZipFile zipFile = new ZipFile(fileName);
                zipFile.size();
            } catch (Exception e){
                result = false;
                LOGGER.error("Patch zip [{}] is not valid. ", fileName, e);
            }
            return result;
        }

    static void copy(InputStream is, OutputStream os) throws IOException {
        try {
            byte[] b = new byte[4096];
            int l = is.read(b);
            while (l >= 0) {
                os.write(b, 0, l);
                l = is.read(b);
            }
        } finally {
            close(os);
        }
    }

    static void close(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (IOException e) {
        }
    }

}
