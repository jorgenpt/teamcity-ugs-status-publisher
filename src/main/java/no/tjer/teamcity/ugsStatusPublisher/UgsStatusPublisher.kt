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

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import jetbrains.buildServer.commitPublisher.*
import jetbrains.buildServer.log.LogUtil
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.*
import jetbrains.buildServer.vcshostings.http.HttpHelper
import jetbrains.buildServer.vcshostings.http.credentials.HttpCredentials
import jetbrains.buildServer.vcshostings.http.credentials.UsernamePasswordCredentials
import no.tjer.teamcity.ugsStatusPublisher.UgsStatusPublisher.BadgeResult
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.text.MessageFormat
import java.util.*

val BuildPromotion.isCanceledOrFailedToStart: Boolean
    get() {
        if (isCanceled) {
            return true;
        }
        return associatedBuild?.isInternalError ?: false
    }

/**
 * Updates Unreal Game Sync commit statuses via REST API.
 */
internal class UgsStatusPublisher(
    settings: CommitStatusPublisherSettings,
    buildType: SBuildType,
    buildFeatureId: String,
    webLinks: WebLinks,
    params: Map<String, String>,
    problems: CommitStatusPublisherProblems
) : HttpBasedCommitStatusPublisher<BadgeResult?>(settings, buildType, buildFeatureId, params, problems, webLinks) {
    override fun toString(): String {
        return UgsConstants.ID
    }

    override fun getId(): String {
        return UgsConstants.ID
    }

    override fun isPublishingForRevision(revision: BuildRevision): Boolean {
        return true
    }

    @Throws(PublisherException::class)
    override fun buildStarted(build: SBuild, revision: BuildRevision): Boolean {
        updateBuildStatus(build, revision, true)
        return true
    }

    @Throws(PublisherException::class)
    override fun buildFinished(build: SBuild, revision: BuildRevision): Boolean {
        updateBuildStatus(build, revision, false)
        return true
    }

    @Throws(PublisherException::class)
    override fun buildInterrupted(build: SBuild, revision: BuildRevision): Boolean {
        updateBuildStatus(build, revision, false)
        return true
    }

    @Throws(PublisherException::class)
    override fun buildMarkedAsSuccessful(build: SBuild, revision: BuildRevision, buildInProgress: Boolean): Boolean {
        updateBuildStatus(build, revision, buildInProgress)
        return true
    }

    @Throws(HttpPublisherException::class, IOException::class)
    override fun processResponse(response: HttpHelper.HttpResponse) {
        if (response.statusCode >= 400) {
            processErrorResponse(response)
        }
    }

    @Throws(PublisherException::class)
    override fun getRevisionStatus(buildPromotion: BuildPromotion, revision: BuildRevision): RevisionStatus? {
        return null // TODO?
    }

    @Throws(PublisherException::class)
    private fun updateBuildStatus(build: SBuild, revision: BuildRevision, isStarting: Boolean) {
        val status = getCommitStatus(build, isStarting)
        val description = LogUtil.describe(build)
        val commitStatusUrl = MessageFormat.format(POST_BADGE_URL_FORMAT, myParams[UgsConstants.SERVER_URL])
        val body = JSONObject()
        // TODO: Try parse?

        // This will return the changelist number if using feature branches (e.g. "main|123" -> 123)
        // or only the changelist number if not using feature branches ("123" -> 123)
        val changeNumber = revision.revision.split("|").last().toInt(10)
        body.put("ChangeNumber", changeNumber)
        body.put("Project", myParams[UgsConstants.PROJECT])
        body.put("BuildType", myParams[UgsConstants.BADGE_NAME])
        body.put("Result", status.result.uGSValue)
        body.put("Url", status.targetUrl)
        postJson(
            commitStatusUrl,
            getCredentials(myParams),
            body.toString(),
            Collections.singletonMap("Accept", "application/json"),
            description
        )
    }

    private fun getCommitStatus(build: SBuild, isStarting: Boolean): CommitStatus {
        val buildPromotion = build.buildPromotion
        // If a dependency has failed for a composite build, report a failure even if we're canceled
        val dependencies = buildPromotion.dependencies.map { it.dependOn }
        if (build.isCompositeBuild) {
            if (dependencies.any { !it.isCanceledOrFailedToStart && it.associatedBuild?.buildStatus?.isFailed == true }) {
                return CommitStatus(BadgeResult.FAILURE, getViewUrl(build))
            }
        }

        val isCanceled = buildPromotion.isCanceled || build.isInternalError
        val badge = getBadge(isStarting, isCanceled, build.buildStatus)
        return CommitStatus(badge, getViewUrl(build))
    }

    private class Error {
        var message: String? = null
    }

    internal class CommitStatus(val result: BadgeResult, val targetUrl: String)
    internal enum class BadgeResult(val displayName: String, val uGSValue: Int) {
        STARTING("Starting", 0), FAILURE("Failure", 1), WARNING("Warning", 2), SUCCESS("Success", 3), SKIPPED(
            "Skipped", 4
        )
    }

    companion object {
        private const val METRICS_URL_FORMAT = "{0}/api/rugs_metrics"
        private const val POST_BADGE_URL_FORMAT = "{0}/api/build"
        private const val ERROR_AUTHORIZATION = "Check username & password for RUGS"
        private const val FAILED_TO_TEST_CONNECTION_TO_REPOSITORY =
            "UGS publisher has failed to test connection to server "
        private val myGson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create()

        @Throws(PublisherException::class)
        fun testConnection(
            params: Map<String, String>, trustStore: KeyStore?
        ) {
            val serverUrl = params[UgsConstants.SERVER_URL]
            val url = MessageFormat.format(METRICS_URL_FORMAT, serverUrl)
            try {
                IOGuard.allowNetworkCall<Exception> {
                    HttpHelper.get<HttpPublisherException>(url,
                        getCredentials(params),
                        Collections.singletonMap<String, String>("Accept", "application/json"),
                        DEFAULT_CONNECTION_TIMEOUT,
                        trustStore,
                        object : DefaultHttpResponseProcessor() {
                            @Throws(HttpPublisherException::class, IOException::class)
                            override fun processResponse(response: HttpHelper.HttpResponse) {
                                val status = response.statusCode
                                if (status == 401 || status == 403) {
                                    throw HttpPublisherException(ERROR_AUTHORIZATION)
                                }

                                // Ignore Bad Request for POST check
                                if (status == 400) {
                                    return
                                }
                                if (status != 200) {
                                    processErrorResponse(response)
                                }
                            }
                        })
                }
            } catch (e: Exception) {
                val message = FAILED_TO_TEST_CONNECTION_TO_REPOSITORY + serverUrl
                LoggerUtil.LOG.debug(message, e)
                throw PublisherException(message, e)
            }
        }

        @Throws(HttpPublisherException::class)
        private fun processErrorResponse(response: HttpHelper.HttpResponse) {
            val status = response.statusCode
            val content =
                response.content ?: throw HttpPublisherException(status, response.statusText, "Empty HTTP response")
            var error: Error? = null
            try {
                error = myGson.fromJson(content, Error::class.java)
            } catch (e: JsonSyntaxException) {
                // Invalid JSON response
            }
            val message = error?.message ?: "HTTP response error"
            throw HttpPublisherException(status, response.statusText, message)
        }

        private fun getBadge(isStarting: Boolean, isCanceled: Boolean, status: Status): BadgeResult {
            return if (!isStarting) {
                if (status.isSuccessful) {
                    BadgeResult.SUCCESS
                } else if (isCanceled) {
                    BadgeResult.SKIPPED
                } else if (status == Status.ERROR) {
                    BadgeResult.FAILURE
                } else if (status == Status.FAILURE) {
                    BadgeResult.FAILURE
                } else if (status == Status.WARNING) {
                    BadgeResult.WARNING
                } else {
                    LoggerUtil.LOG.warn("Unknown status $status")
                    BadgeResult.SKIPPED
                }
            } else BadgeResult.STARTING
        }

        private fun getCredentials(params: Map<String, String>): HttpCredentials? {
            val user = params[UgsConstants.AUTH_USER]
            val password = params[UgsConstants.AUTH_PASSWORD]
            return if (user != null && password != null) {
                UsernamePasswordCredentials(user, password)
            } else {
                null
            }
        }
    }
}
