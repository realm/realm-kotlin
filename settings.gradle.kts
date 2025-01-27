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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

rootProject.name = "realm-kotlin-root"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
//            if (requested.id.id.startsWith("org.example.buildsrc")) {
//                useModule("org.example:buildSrc:${pluginManagement.version}")
//            }
            if (requested.id.id == "kotlinx-atomicfu") {
                useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.27.0")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":packages:gradle-plugin")
include(":packages:plugin-compiler")
include(":packages:plugin-compiler-shaded")
include(":packages:library-base")
include(":packages:library-sync")
include(":packages:cinterop")
include(":packages:jni-swig-stub")

include(":packages:test-base")
include(":packages:test-sync")
