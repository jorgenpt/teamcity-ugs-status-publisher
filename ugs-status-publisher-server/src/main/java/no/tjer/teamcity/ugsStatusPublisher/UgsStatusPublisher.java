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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.security.KeyStore;
import java.text.MessageFormat;
import java.util.*;

import jetbrains.buildServer.commitPublisher.*;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import static jetbrains.buildServer.commitPublisher.LoggerUtil.LOG;


/**
 * Updates TFS Git commit statuses via REST API.
 */
class UgsStatusPublisher extends HttpBasedCommitStatusPublisher<UgsStatusPublisher.BadgeResult> {

    private static final String METRICS_URL_FORMAT = "{0}/api/rugs_metrics";
    private static final String POST_BADGE_URL_FORMAT = "{0}/api/build";
    private static final String ERROR_AUTHORIZATION = "Check username & password for RUGS";
    private static final String FAILED_TO_TEST_CONNECTION_TO_REPOSITORY = "UGS publisher has failed to test connection to server ";
    private static final Gson myGson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
            .create();

    UgsStatusPublisher(@NotNull final CommitStatusPublisherSettings settings,
                       @NotNull final SBuildType buildType,
                       @NotNull final String buildFeatureId,
                       @NotNull final WebLinks webLinks,
                       @NotNull final Map<String, String> params,
                       @NotNull final CommitStatusPublisherProblems problems) {
        super(settings, buildType, buildFeatureId, params, problems, webLinks);
    }

    @NotNull
    public String toString() {
        return UgsConstants.ID;
    }

    @NotNull
    @Override
    public String getId() {
        return UgsConstants.ID;
    }

    @Override
    public boolean isPublishingForRevision(@NotNull final BuildRevision revision) {
        return true;
    }

    @Override
    public boolean buildStarted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
        updateBuildStatus(build, revision, true);
        return true;
    }

    @Override
    public boolean buildFinished(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
        updateBuildStatus(build, revision, false);
        return true;
    }

    @Override
    public boolean buildInterrupted(@NotNull SBuild build, @NotNull BuildRevision revision) throws PublisherException {
        updateBuildStatus(build, revision, false);
        return true;
    }

    @Override
    public boolean buildMarkedAsSuccessful(@NotNull final SBuild build, @NotNull final BuildRevision revision, final boolean buildInProgress) throws PublisherException {
        updateBuildStatus(build, revision, buildInProgress);
        return true;
    }

    @Override
    public void processResponse(@NotNull final HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
        if (response.getStatusCode() >= 400) {
            processErrorResponse(response);
        }
    }

    @Override
    public RevisionStatus getRevisionStatus(@NotNull BuildPromotion buildPromotion, @NotNull BuildRevision revision) throws PublisherException {
        return null;  // TODO?
    }

    public static void testConnection(@NotNull final Map<String, String> params,
                                      @Nullable final KeyStore trustStore) throws PublisherException {

        String serverUrl = params.get(UgsConstants.SERVER_URL);
        String url = MessageFormat.format(METRICS_URL_FORMAT, serverUrl);
        try {
            IOGuard.allowNetworkCall(() -> {
                HttpHelper.get(url, getUserName(params), getPassword(params), Collections.singletonMap("Accept", "application/json"), BaseCommitStatusPublisher.DEFAULT_CONNECTION_TIMEOUT,
                        trustStore, new DefaultHttpResponseProcessor() {
                            @Override
                            public void processResponse(HttpHelper.HttpResponse response) throws HttpPublisherException, IOException {
                                final int status = response.getStatusCode();
                                if (status == 401 || status == 403) {
                                    throw new HttpPublisherException(ERROR_AUTHORIZATION);
                                }

                                // Ignore Bad Request for POST check
                                if (status == 400) {
                                    return;
                                }

                                if (status != 200) {
                                    processErrorResponse(response);
                                }
                            }
                        });
            });

        } catch (Exception e) {
            final String message = FAILED_TO_TEST_CONNECTION_TO_REPOSITORY + serverUrl;
            LOG.debug(message, e);
            throw new PublisherException(message, e);
        }
    }

    private static void processErrorResponse(@NotNull final HttpHelper.HttpResponse response) throws HttpPublisherException {
        final int status = response.getStatusCode();
        final String content = response.getContent();
        if (null == content) {
            throw new HttpPublisherException(status, response.getStatusText(), "Empty HTTP response");
        }

        Error error = null;
        try {
            error = myGson.fromJson(content, Error.class);
        } catch (JsonSyntaxException e) {
            // Invalid JSON response
        }

        final String message;
        if (error != null && error.message != null) {
            message = error.message;
        } else {
            message = "HTTP response error";
        }

        throw new HttpPublisherException(status, response.getStatusText(), message);
    }

    private void updateBuildStatus(@NotNull SBuild build, @NotNull BuildRevision revision, boolean isStarting) throws PublisherException {
        final CommitStatus status = getCommitStatus(build, isStarting);
        final String description = LogUtil.describe(build);
        final String commitStatusUrl = MessageFormat.format(POST_BADGE_URL_FORMAT, myParams.get(UgsConstants.SERVER_URL));

        JSONObject body = new JSONObject();
        // TODO: Try parse?
        body.put("ChangeNumber", Integer.parseInt(revision.getRevision(), 10));
        body.put("Project", myParams.get(UgsConstants.PROJECT));
        body.put("BuildType", myParams.get(UgsConstants.BADGE_NAME));
        body.put("Result", status.result.getUGSValue());
        body.put("Url", status.targetUrl);

        postJson(commitStatusUrl, getUserName(myParams), getPassword(myParams),
                body.toString(),
                Collections.singletonMap("Accept", "application/json"),
                description
        );
    }

    @NotNull
    private CommitStatus getCommitStatus(final SBuild build, final boolean isStarting) {
        BuildPromotion buildPromotion = build.getBuildPromotion();
        boolean isCanceled = buildPromotion.isCanceled() || build.isInternalError();
        LOG.warn(String.format("UGS %s: isStarting: %s isCanceled: %s isISE: %s Status: %s", build.getFullName(), isStarting, buildPromotion.isCanceled(), build.isInternalError(), build.getBuildStatus()));
        BadgeResult badge = getBadge(isStarting, isCanceled, build.getBuildStatus());
        return new CommitStatus(badge, getViewUrl(build));
    }

    private static BadgeResult getBadge(boolean isStarting, boolean isCanceled, Status status) {
        if (!isStarting) {
            if (status.isSuccessful()) return BadgeResult.SUCCESS;
            else if (status == Status.ERROR) return BadgeResult.FAILURE;
            else if (status == Status.FAILURE) return BadgeResult.FAILURE;
            else if (isCanceled) return BadgeResult.SKIPPED;
            else if (status == Status.WARNING) return BadgeResult.WARNING;
            else {
                LOG.warn("Unknown status " + status);
                return BadgeResult.SKIPPED;
            }
        }

        return BadgeResult.STARTING;
    }

    @Nullable
    private static String getUserName(Map<String, String> params) {
        final String user = params.get(UgsConstants.AUTH_USER);
        final String password = params.get(UgsConstants.AUTH_PASSWORD);
        return password != null ? user : null;
    }

    @Nullable
    private static String getPassword(Map<String, String> params) {
        return params.get(UgsConstants.AUTH_PASSWORD);
    }

    private static class Error {
        String message;
    }

    static class CommitStatus {
        @NotNull
        final BadgeResult result;
        final String targetUrl;

        public CommitStatus(@NotNull BadgeResult result, String targetUrl) {
            this.result = result;
            this.targetUrl = targetUrl;
        }
    }

    enum BadgeResult {
        STARTING("Starting", 0),
        FAILURE("Failure", 1),
        WARNING("Warning", 2),
        SUCCESS("Success", 3),
        SKIPPED("Skipped", 4);

        private final String displayName;
        private final int ugsValue;

        BadgeResult(String displayName, int ugsValue) {
            this.displayName = displayName;
            this.ugsValue = ugsValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getUGSValue() {
            return ugsValue;
        }
    }
}
