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

import jetbrains.buildServer.agent.Constants

class UgsConstants {
    companion object {
        const val ID = "ugs"
        const val SERVER_URL = "ugsServerUrl"
        const val AUTH_USER = "ugsAuthUser"
        const val AUTH_PASSWORD = Constants.SECURE_PROPERTY_PREFIX + "ugsAuthPassword"
        const val PROJECT = "ugsProject"
        const val BADGE_NAME = "ugsBadgeName"
    }

    // Accessors for ugsSettings.jsp
    @Suppress("unused")
    fun getServerUrl() = SERVER_URL

    @Suppress("unused")
    fun getAuthUser() = AUTH_USER

    @Suppress("unused")
    fun getAuthPassword() = AUTH_PASSWORD

    @Suppress("unused")
    fun getProject() = PROJECT

    @Suppress("unused")
    fun getBadgeName() = BADGE_NAME
}
