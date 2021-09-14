/*
 * Copyright 2021 Realm Inc.
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

package io.realm.jvm

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.Enumeration
import java.util.LinkedList
import java.util.Properties

/**
 * Load the C++ dynamic libraries from a the fat Jar.
 * The fat Jar contains three platforms (Win, Linux and Mac) the loader detects the host platform
 * then extract and install the libraries in the same order specified in the 'dynamic_libraries.properties' file.
 *
 * Note: this class should be invoke dynamically using reflection so the classloader can have accesses
 * to the dynamic libraries files located inside the fat Jar.
 */
class SoLoader {
    private val platform: Platform = Platform.currentOS()
    private val libs: MutableList<Pair<String, String>> = mutableListOf()

    init {
        readLibrariesHashes()
    }

    fun load() {
        // load the libraries in the reverse order of dependency specified in 'dynamic_libraries.properties'
        for (lib in libs) {
            load(libraryName = lib.first, expectedHash = lib.second)
        }
    }

    private fun load(libraryName: String, expectedHash: String) {
        // load the embedded .so file located inside the Jar file.
        // unpacking the file is skipped if the hash of the file is already installed.
        // instead, the on disk file will be loaded.

        // for each SO file
        // check if the library is already installed in the default platform location
        // path should be <default user lib dir>/io.realm.kotlin/hash/librealmffi.so
        // if the full path exists (and the on disk hash matches) then load it otherwise unpack and load it.
        val libraryInstallationLocation: File = defaultAbsolutePath(libraryName, expectedHash)
        if (!libraryInstallationLocation.exists()) {
            unpackAndInstall(libraryName, libraryInstallationLocation, expectedHash)
        } else {
            // only double check the installed lib hash (in case it was tampered with locally)
            validHashOrThrow(libraryInstallationLocation, expectedHash)
        }
        @Suppress("UnsafeDynamicallyLoadedCode")
        // System.loadLibrary does not accept a full path to the lib (needs to be in the current Java paths)
        System.load(libraryInstallationLocation.absolutePath)
    }

    private fun readLibrariesHashes() {
        javaClass.getResourceAsStream("${platform.shortName}/dynamic_libraries.properties").use { props ->
            OrderedProperties().run {
                load(props)
                for (libName in keys()) {
                    libs.add(Pair(libName as String, get(libName) as String))
                }
            }
        }
    }

    private fun defaultAbsolutePath(libraryName: String, libraryHash: String): File {
        return File(
            platform.defaultSystemLocation + File.separator +
                libraryHash + File.separator +
                (platform.prefix + libraryName + "." + platform.suffix)
        )
    }

    private fun libPathInsideJar(libraryName: String) =
        "${platform.shortName}/${platform.prefix}$libraryName.${platform.suffix}"

    private fun unpackAndInstall(libraryName: String, absolutePath: File, expectedHash: String) {
        absolutePath.parentFile.mkdirs()
        javaClass.getResourceAsStream(libPathInsideJar(libraryName)).use { lib ->
            Files.newOutputStream(absolutePath.toPath()).use {
                lib.copyTo(it)
            }
        }
        // after unpacking make sure the hash is valid
        validHashOrThrow(absolutePath, expectedHash)
    }

    private fun validHashOrThrow(file: File, expectedHash: String, cleanup: Boolean = true) {
        if (!isValidHash(file, expectedHash)) {
            if (cleanup) {
                file.delete()
            }
            throw error("Corrupt or invalid hash for ${file.absolutePath} expected hash is $expectedHash")
        }
    }

    private fun isValidHash(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(file.toPath()).use {
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val bytes = it.read(buf)
                if (bytes > 0) {
                    digest.update(buf, 0, bytes)
                } else {
                    break
                }
            }
            val hash = digest.digest().toHexString()
            return hash == expected
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("", transform = { "%02x".format(it) })
}

private enum class Platform(
    val shortName: String,
    val prefix: String,
    val suffix: String,
    val defaultSystemLocation: String
) {
    MACOS(
        shortName = "/jni/macos",
        prefix = "lib",
        suffix = "dylib",
        defaultSystemLocation = "${System.getProperty("user.home")}/Library/Caches/io.realm.kotlin/"
    ),
    LINUX(
        shortName = "/jni/linux",
        prefix = "lib",
        suffix = "so",
        defaultSystemLocation = "${System.getProperty("user.home")}/.cache/io-realm.kotlin/"
    ),
    WINDOWS(
        shortName = "/jni/win",
        prefix = "",
        suffix = "dll",
        defaultSystemLocation = (
            System.getenv("LOCALAPPDATA")
                ?: "${System.getProperty("user.home")}/AppData/Local"
            ) + "/io-realm-kotlin/"
    );

    companion object {
        fun currentOS(): Platform {
            val os = System.getProperty("os.name").lowercase(Locale.getDefault())
            return when {
                os.contains("jni/win") -> {
                    WINDOWS
                }
                os.contains("nix") || os.contains("nux") || os.contains("aix") -> {
                    LINUX
                }
                os.contains("mac") -> {
                    MACOS
                }
                else -> error("Unsupported OS: $os")
            }
        }
    }
}

private const val BUFFER_SIZE = 16384 // 16k

// Preserve the insertion orders for the keys in order to load
// the dynamic libraries in the reverse order specified in the property file.
private class OrderedProperties : Properties() {
    private val orderedKeys = LinkedList<Any>()

    override fun put(key: Any?, value: Any?): Any? {
        orderedKeys.add(key!!)
        return super.put(key, value)
    }

    override fun keys(): Enumeration<Any> {
        return Collections.enumeration(orderedKeys)
    }
}
