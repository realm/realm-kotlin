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

plugins {
    id("com.android.library") apply false
    id("realm-lint")
    `java-gradle-plugin`
}

buildscript {
    extra["ciBuild"] = Realm.ciBuild
    repositories {
        if (extra["ciBuild"] as Boolean) {
            maven("file://${rootProject.rootDir.absolutePath}/../packages/build/m2-buildrepo")
        }
    }
    dependencies {
        classpath("io.realm.kotlin:gradle-plugin:${Realm.version}")
    }
}

allprojects {
    repositories {
        if (rootProject.extra["ciBuild"] as Boolean) {
            maven("file://${rootProject.rootDir.absolutePath}/../packages/build/m2-buildrepo")
        }
        google()
        mavenCentral()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "${Versions.jvmTarget}"
    }
}
