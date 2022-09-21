/*
 * Copyright 2022 Realm Inc.
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
package io.realm.kotlin.test.mongodb.util

import io.ktor.http.HttpMethod
import kotlin.native.concurrent.SharedImmutable

// Sits here because [SharedImmutable] annotation is only available in common.
@SharedImmutable
val TEST_METHODS = listOf(
    HttpMethod.Get,
    HttpMethod.Post,
    // PATCH is currently broken on macOS if you read the content, which
    // our NetworkTransport does: https://youtrack.jetbrains.com/issue/KTOR-4101/JsonFeature:-HttpClient-always-timeout-when-sending-PATCH-reques
    // So for now, ignore PATCH in our tests. User API's does not use this anyway, only
    // the AdminAPI, which has a work-around.
    // HttpMethod.Patch,
    HttpMethod.Put,
    HttpMethod.Delete,
)
