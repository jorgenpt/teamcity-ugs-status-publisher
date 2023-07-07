/*
 * Copyright 2023 Jørgen Tjernø <jorgen@tjer.no>
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
package no.tjer.teamcity.ugsStatusPublisher

import jetbrains.buildServer.commitPublisher.*
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.serverSide.auth.SecurityContext
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.util.ssl.SSLTrustStoreProvider
import jetbrains.buildServer.vcs.VcsRoot
import jetbrains.buildServer.web.openapi.PluginDescriptor

/**
 * Settings for Unreal Game Sync commit status publisher.
 */
class UgsPublisherSettings(
    descriptor: PluginDescriptor,
    links: WebLinks,
    problems: CommitStatusPublisherProblems,
    private val mySecurityContext: SecurityContext,
    trustStoreProvider: SSLTrustStoreProvider
) : BasePublisherSettings(descriptor, links, problems, trustStoreProvider), CommitStatusPublisherSettings {
    override fun getId(): String {
        return UgsConstants.ID
    }

    override fun getName(): String {
        return "Unreal Game Sync"
    }

    override fun getEditSettingsUrl(): String {
        return myDescriptor.getPluginResourcesPath("ugsSettings.jsp")
    }

    override fun isTestConnectionSupported(): Boolean {
        return true
    }

    @Throws(PublisherException::class)
    override fun testConnection(buildTypeOrTemplate: BuildTypeIdentity, root: VcsRoot, params: Map<String, String>) {
        UgsStatusPublisher.testConnection(params, trustStore())
    }

    override fun createPublisher(
        buildType: SBuildType, buildFeatureId: String, params: Map<String, String>
    ): CommitStatusPublisher {
        return UgsStatusPublisher(this, buildType, buildFeatureId, myLinks, params, myProblems)
    }

    override fun describeParameters(params: Map<String, String>): String {
        return String.format("Post commit status to %s", name)
    }

    override fun getParametersProcessor(buildTypeOrTemplate: BuildTypeIdentity): PropertiesProcessor {
        return object : PropertiesProcessor {
            private fun checkNotEmpty(
                properties: Map<String, String>, key: String, message: String, res: MutableCollection<InvalidProperty>
            ): Boolean {
                if (isEmpty(properties, key)) {
                    res.add(InvalidProperty(key, message))
                    return true
                }
                return false
            }

            private fun isEmpty(
                properties: Map<String, String>, key: String
            ): Boolean {
                return StringUtil.isEmptyOrSpaces(properties[key])
            }

            override fun process(p: Map<String, String>?): Collection<InvalidProperty> {
                val result: MutableCollection<InvalidProperty> = ArrayList()
                if (p == null) return result
                checkNotEmpty(p, UgsConstants.SERVER_URL, "URL must be specified", result)
                // TODO: Check Project formatting as a Perforce URL?
                checkNotEmpty(p, UgsConstants.PROJECT, "Project must be specified", result)
                checkNotEmpty(p, UgsConstants.BADGE_NAME, "Badge name must be specified", result)
                return result
            }
        }
    }

    override fun isPublishingForVcsRoot(root: VcsRoot): Boolean {
        return true
    }

    override fun getSupportedEvents(
        buildType: SBuildType, params: Map<String, String>
    ): Set<CommitStatusPublisher.Event> {
        return if (isBuildQueuedSupported(buildType, params)) mySupportedEventsWithQueued else mySupportedEvents
    }

    companion object {
        private val mySupportedEvents: Set<CommitStatusPublisher.Event> = setOf(
            CommitStatusPublisher.Event.STARTED,
            CommitStatusPublisher.Event.FINISHED,
            CommitStatusPublisher.Event.INTERRUPTED,
            CommitStatusPublisher.Event.MARKED_AS_SUCCESSFUL
        )
        private val mySupportedEventsWithQueued: Set<CommitStatusPublisher.Event> = mySupportedEvents + setOf(
            CommitStatusPublisher.Event.QUEUED, CommitStatusPublisher.Event.REMOVED_FROM_QUEUE
        )
    }
}
