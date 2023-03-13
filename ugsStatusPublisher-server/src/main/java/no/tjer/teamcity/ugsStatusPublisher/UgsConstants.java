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

import jetbrains.buildServer.agent.Constants;

public class UgsConstants {
    public static final String ID = "ugs";
    public static final String SERVER_URL = "ugsServerUrl";
    public static final String AUTH_USER = "ugsAuthUser";
    public static final String AUTH_PASSWORD = Constants.SECURE_PROPERTY_PREFIX + "ugsAuthPassword";
    public static final String PROJECT = "ugsProject";
    public static final String BADGE_NAME = "ugsBadgeName";

    public String getServerUrl() {
        return SERVER_URL;
    }

    public String getAuthUser() {
        return AUTH_USER;
    }

    public String getAuthPassword() {
        return AUTH_PASSWORD;
    }

    public String getProject() {
        return PROJECT;
    }

    public String getBadgeName() {
        return BADGE_NAME;
    }

}
