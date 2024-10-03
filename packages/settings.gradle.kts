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
        // Project setup - See './CONTRIBUTING.md' for description of the project structure and various options.
        getPropertyValue("testRepository")?.let {
            maven("file://${rootDir.absolutePath}/$it")
        }
//        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}
fun getPropertyValue(propertyName: String): String? {
    val systemValue: String? = System.getenv(propertyName)
    if (extra.has(propertyName)) {
        return extra[propertyName] as String?
    }
    return systemValue
}

(getPropertyValue("includeSdkModules")?.let { it.toBoolean() } ?: true).let {
    if (it) {
        include(":gradle-plugin")
        include(":plugin-compiler")
        include(":plugin-compiler-shaded")
        include(":library-base")
        include(":cinterop")
        include(":jni-swig-stub")
    }
}
(getPropertyValue("includeTestModules")?.let { it.toBoolean() } ?: true).let {
    if (it) {
        include(":test-base")
    }
}
