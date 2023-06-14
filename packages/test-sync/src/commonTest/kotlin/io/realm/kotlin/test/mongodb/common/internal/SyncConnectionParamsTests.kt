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
@file:Suppress("invisible_member", "invisible_reference")
package io.realm.kotlin.test.mongodb.common.internal

import io.realm.kotlin.internal.interop.SyncConnectionParams
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verify that input to io.realm.kotlin.internal.interop.SyncConnectionParams are bucket correctly
 */
internal class SyncConnectionParamsTests {

    @Test
    fun allProperties() {
        val props = SyncConnectionParams(
            sdkVersion = "sdkVersion",
            localAppName = "appName",
            localAppVersion = "appVersion",
            platform = "platform",
            platformVersion = "platformVersion",
            cpuArch = "cpuArch",
            device = "device",
            deviceVersion = "deviceVersion",
            framework = SyncConnectionParams.Runtime.JVM,
            frameworkVersion = "frameworkVersion"
        )
        assertEquals("Kotlin", props.sdkName)
        assertEquals("sdkVersion", props.sdkVersion)
        assertEquals("appName", props.localAppName)
        assertEquals("appVersion", props.localAppVersion)
        assertEquals("Unknown (platform)", props.platform)
        assertEquals("platformVersion", props.platformVersion)
        assertEquals("Unknown (cpuArch)", props.cpuArch)
        assertEquals("device", props.device)
        assertEquals("deviceVersion", props.deviceVersion)
        assertEquals("JVM", props.framework)
        assertEquals("frameworkVersion", props.frameworkVersion)
    }

    @Test
    fun knownPlatform() {
        val mapping = mapOf(
            "Linux" to listOf("Linux", "linux", "liNux 5.4.3", "fooLinux 5.4.3", "Linux emulator - MacOS"),
            "Windows" to listOf("Windows", "windows", "winDows 11", "barWindows 11", "Windows emulator - Linux"),
            "MacOS" to listOf("MACOS", "macos", "Mac OS X", "foo macosX", "NSMACHOperatingSystem", "nsmachoperatingsystem", "iOS emulator - MacOS"),
            "iOS" to listOf("IOS", "ios", "iOS 13.1.2", "fooiOS 13.1.2", "Android emulator - iOS"),
            "Android" to listOf("ANDROID", "android", "anDroid 33", "fooAndroid 33"),
            "" to listOf("")
        )

        mapping.forEach { entry: Map.Entry<String, List<String>> ->
            entry.value.forEach { platformValue ->
                val props = SyncConnectionParams(
                    sdkVersion = "",
                    localAppName = "",
                    localAppVersion = "",
                    platform = platformValue,
                    platformVersion = "",
                    cpuArch = "",
                    device = "",
                    deviceVersion = "",
                    framework = SyncConnectionParams.Runtime.JVM,
                    frameworkVersion = ""
                )
                assertEquals(entry.key, props.platform, "$platformValue failed")
            }
        }
    }

    @Test
    fun unknownPlatform() {
        val unknownPlatforms = listOf(
            "Foo Bar",
            "win dows",
            "li\$nux",
            "mac-os"
        )
        unknownPlatforms.forEach { platform ->
            val props = SyncConnectionParams(
                sdkVersion = "",
                localAppName = "",
                localAppVersion = "",
                platform = platform,
                platformVersion = "",
                cpuArch = "",
                device = "",
                deviceVersion = "",
                framework = SyncConnectionParams.Runtime.JVM,
                frameworkVersion = ""
            )
            assertEquals("Unknown ($platform)", props.platform, "$platform failed.")
        }
    }

    @Test
    fun knownCpuArch() {
        val mapping = mapOf(
            "x86" to listOf("x86", "X86", "x86 (windows)"),
            "x86_64" to listOf("x86_64", "X86_64", "x86-64", "Linux x86_64"),
            "armeabi-v7a" to listOf("armeabi-v7a", "ARMEABI-V7A", "arm-v7a", "v7a"),
            "arm64" to listOf("arm64", "ARM64", "arm64-v8a", "Linux arm64", "aarch64", "AARCH64"),
            "" to listOf("")
        )

        mapping.forEach { entry: Map.Entry<String, List<String>> ->
            entry.value.forEach { cpuArch ->
                val props = SyncConnectionParams(
                    sdkVersion = "",
                    localAppName = "",
                    localAppVersion = "",
                    platform = "",
                    platformVersion = "",
                    cpuArch = cpuArch,
                    device = "",
                    deviceVersion = "",
                    framework = SyncConnectionParams.Runtime.JVM,
                    frameworkVersion = ""
                )
                assertEquals(entry.key, props.cpuArch, "$cpuArch failed")
            }
        }
    }
}
