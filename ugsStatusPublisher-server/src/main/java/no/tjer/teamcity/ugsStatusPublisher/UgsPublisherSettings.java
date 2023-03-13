/*
 * Copyright 2000-2022 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.tjer.teamcity.ugsStatusPublisher;

import java.util.*;

import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.commitPublisher.CommitStatusPublisher.Event;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Settings for Unreal Game Sync commit status publisher.
 */
public class UgsPublisherSettings extends BasePublisherSettings implements CommitStatusPublisherSettings {

    private final SecurityContext mySecurityContext;
    private static final Set<Event> mySupportedEvents = new HashSet<Event>() {{
        add(Event.STARTED);
        add(Event.FINISHED);
        add(Event.INTERRUPTED);
        add(Event.MARKED_AS_SUCCESSFUL);
    }};

    private static final Set<Event> mySupportedEventsWithQueued = new HashSet<Event>() {{
        add(Event.QUEUED);
        add(Event.REMOVED_FROM_QUEUE);
        addAll(mySupportedEvents);
    }};

    public UgsPublisherSettings(@NotNull PluginDescriptor descriptor,
                                @NotNull WebLinks links,
                                @NotNull CommitStatusPublisherProblems problems,
                                @NotNull SecurityContext securityContext,
                                @NotNull SSLTrustStoreProvider trustStoreProvider) {
        super(descriptor, links, problems, trustStoreProvider);
        mySecurityContext = securityContext;
    }

    @NotNull
    public String getId() {
        return UgsConstants.ID;
    }

    @NotNull
    public String getName() {
        return "Unreal Game Sync";
    }

    @Nullable
    public String getEditSettingsUrl() {
        return myDescriptor.getPluginResourcesPath("ugsSettings.jsp");
    }

    @Override
    public boolean isTestConnectionSupported() {
        return true;
    }

    @Override
    public void testConnection(@NotNull BuildTypeIdentity buildTypeOrTemplate, @NotNull VcsRoot root, @NotNull Map<String, String> params) throws PublisherException {
        UgsStatusPublisher.testConnection(params, trustStore());
    }

    @Nullable
    public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
        return new UgsStatusPublisher(this, buildType, buildFeatureId, myLinks, params, myProblems);
    }

    @NotNull
    public String describeParameters(@NotNull Map<String, String> params) {
        return String.format("Post commit status to %s", getName());
    }

    @Nullable
    public PropertiesProcessor getParametersProcessor(@NotNull BuildTypeIdentity buildTypeOrTemplate) {
        return new PropertiesProcessor() {
            private boolean checkNotEmpty(@NotNull final Map<String, String> properties,
                                          @NotNull final String key,
                                          @NotNull final String message,
                                          @NotNull final Collection<InvalidProperty> res) {
                if (isEmpty(properties, key)) {
                    res.add(new InvalidProperty(key, message));
                    return true;
                }
                return false;
            }

            private boolean isEmpty(@NotNull final Map<String, String> properties,
                                    @NotNull final String key) {
                return StringUtil.isEmptyOrSpaces(properties.get(key));
            }

            @NotNull
            public Collection<InvalidProperty> process(@Nullable final Map<String, String> p) {
                final Collection<InvalidProperty> result = new ArrayList<InvalidProperty>();
                if (p == null) return result;

                checkNotEmpty(p, UgsConstants.SERVER_URL, "URL must be specified", result);
                // TODO: Check Project formatting as a Perforce URL?
                checkNotEmpty(p, UgsConstants.PROJECT, "Project must be specified", result);
                checkNotEmpty(p, UgsConstants.BADGE_NAME, "Badge name must be specified", result);
                return result;
            }
        };
    }

    @Override
    public boolean isPublishingForVcsRoot(final VcsRoot root) {
        return true;
    }

    @Override
    protected Set<Event> getSupportedEvents(final SBuildType buildType, final Map<String, String> params) {
        return isBuildQueuedSupported(buildType, params) ? mySupportedEventsWithQueued : mySupportedEvents;
    }
}
