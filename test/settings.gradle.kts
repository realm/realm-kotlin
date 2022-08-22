/*
 * Copyright 2020 Realm Inc.
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

// For local development, we use composite builds.
// For CI buils, the packages are expected to have
// been built and deployed to a local filesystem
// maven repo.
if (System.getenv("CI") == null) {
    includeBuild("../packages")
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

include("base")
include("sync")
