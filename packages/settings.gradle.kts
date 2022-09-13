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

rootProject.name = "realm-kotlin"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// See gradle.properties for a description of the testRepository
val testRepository = if (extra.has("testRepository")) extra["testRepository"] else ""
when(testRepository) {
    "" -> {
        include("gradle-plugin")
        include("plugin-compiler")
        include("plugin-compiler-shaded")
        include("library-base")
        include("library-sync")
        include(":cinterop")
        include(":jni-swig-stub")
    }
    "mavenLocal" -> {
        dependencyResolutionManagement {
            repositories {
                mavenLocal()
            }
        }
    }
    else -> { testRepository
        dependencyResolutionManagement {
            repositories {
                maven("file://${rootDir.absolutePath}/$testRepository")
            }
        }
    }
}
// Always include :test-base and :test-sync
include(":test-base")
include(":test-sync")
