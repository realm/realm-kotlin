/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.mongodb.internal

import io.ktor.http.Url
import io.realm.kotlin.mongodb.HttpLogObfuscator
import io.realm.kotlin.mongodb.internal.RegexPatternReplacer.Companion.defaultObfuscators

internal enum class ObfuscatorFeature(internal val urlPath: String) {
    FEATURE_EMAIL_REGISTER(RegexPatternReplacer.EMAIL_PASSWORD_REGISTER),
    FEATURE_EMAIL_LOGIN(RegexPatternReplacer.EMAIL_PASSWORD_LOGIN),
    FEATURE_API_KEY_CREATE(RegexPatternReplacer.API_KEY_REGISTER),
    FEATURE_API_KEY_LOGIN(RegexPatternReplacer.API_KEY_LOGIN),
    FEATURE_APPLE_LOGIN(RegexPatternReplacer.APPLE_LOGIN),
    FEATURE_FACEBOOK_LOGIN(RegexPatternReplacer.FACEBOOK_LOGIN),
    FEATURE_GOOGLE_LOGIN(RegexPatternReplacer.GOOGLE_LOGIN),
    FEATURE_JWT_LOGIN(RegexPatternReplacer.JWT_LOGIN),
    FEATURE_CUSTOM_FUNCTION_REQUEST(RegexPatternReplacer.FUNCTIONS)
}

internal class HttpLogObfuscatorImpl(
    private val features: Collection<ObfuscatorFeature> = setOf(
        ObfuscatorFeature.FEATURE_EMAIL_REGISTER,
        ObfuscatorFeature.FEATURE_EMAIL_LOGIN,
        ObfuscatorFeature.FEATURE_API_KEY_CREATE,
        ObfuscatorFeature.FEATURE_API_KEY_LOGIN,
        ObfuscatorFeature.FEATURE_APPLE_LOGIN,
        ObfuscatorFeature.FEATURE_FACEBOOK_LOGIN,
        ObfuscatorFeature.FEATURE_GOOGLE_LOGIN,
        ObfuscatorFeature.FEATURE_JWT_LOGIN,
        ObfuscatorFeature.FEATURE_CUSTOM_FUNCTION_REQUEST
    ),
    private val regexReplacerMap: Map<String, RegexPatternReplacer> = defaultObfuscators
) : HttpLogObfuscator {

    override fun obfuscate(input: String): String {
        features.forEach { feature ->
            urlRegex.find(input)?.let { matchResult ->
                val url = Url(matchResult.value)
                if (url.encodedPath.contains(feature.urlPath)) {
                    val patternReplacer = regexReplacerMap[feature.urlPath]
                    return patternReplacer?.findAndReplace(input) ?: input
                }
            }
        }
        return input
    }

    companion object {
        internal val urlRegex =
            Regex("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()!@:%_\\+.~#?&\\/\\/=]*)")
    }
}

internal interface RegexPatternReplacer {
    fun findAndReplace(input: String): String

    companion object {

        private const val AUTH = "auth"
        internal const val FUNCTIONS = "functions/call"

        private const val CREDENTIALS_PROVIDER = "$AUTH/providers"

        private const val PROVIDER_EMAIL_PASSWORD = "$CREDENTIALS_PROVIDER/local-userpass"
        private const val PROVIDER_API_KEY = "$CREDENTIALS_PROVIDER/api-key"
        private const val PROVIDER_APPLE = "$CREDENTIALS_PROVIDER/oauth2-apple"
        private const val PROVIDER_FACEBOOK = "$CREDENTIALS_PROVIDER/oauth2-facebook"
        private const val PROVIDER_GOOGLE = "$CREDENTIALS_PROVIDER/oauth2-google"
        private const val PROVIDER_JWT = "$CREDENTIALS_PROVIDER/custom-token"

        internal const val EMAIL_PASSWORD_REGISTER = "$PROVIDER_EMAIL_PASSWORD/register"
        internal const val EMAIL_PASSWORD_LOGIN = "$PROVIDER_EMAIL_PASSWORD/login"

        internal const val API_KEY_REGISTER = "$AUTH/api_keys" // Key creation done using a different path!
        internal const val API_KEY_LOGIN = "$PROVIDER_API_KEY/login"

        internal const val APPLE_LOGIN = "$PROVIDER_APPLE/login"

        internal const val FACEBOOK_LOGIN = "$PROVIDER_FACEBOOK/login"

        internal const val GOOGLE_LOGIN = "$PROVIDER_GOOGLE/login"

        internal const val JWT_LOGIN = "$PROVIDER_JWT/login"

        // Keys to be replaced
        private const val API_KEY_KEY = "key"
        private const val PASSWORD_KEY = "password"
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val AUTHCODE_KEY = "authCode"
        private const val ID_TOKEN_KEY = "id_token"
        private const val TOKEN_KEY = "token"
        private const val FB_ACCESS_TOKEN_KEY = "accessToken"

        val defaultObfuscators: Map<String, RegexPatternReplacer> = mapOf(
            EMAIL_PASSWORD_REGISTER to registerEmailPassword(),
            EMAIL_PASSWORD_LOGIN to loginEmailPassword(),
            API_KEY_REGISTER to createApiKey(),
            API_KEY_LOGIN to loginApiKey(),
            APPLE_LOGIN to loginApple(),
            FACEBOOK_LOGIN to loginFacebook(),
            GOOGLE_LOGIN to loginGoogle(),
            JWT_LOGIN to loginJwt(),
            FUNCTIONS to customFunction(),
        )

        /**
         * Creates an obfuscator for email- and password-related login requests.
         * It will replace `"password":"<PASSWORD>"` with `"password":"***"`.
         */
        private fun registerEmailPassword(): RegexPatternReplacer = mapOf(
            """(("$PASSWORD_KEY"):(".+?"))""".toRegex() to """"$PASSWORD_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        /**
         * TODO
         */
        private fun loginEmailPassword(): RegexPatternReplacer = mapOf(
            """(("$PASSWORD_KEY"):(".+?"))""".toRegex() to """"$PASSWORD_KEY":"***"""",
            """(("$ACCESS_TOKEN_KEY"):(".+?"))""".toRegex() to """"$ACCESS_TOKEN_KEY":"***"""",
            """(("$REFRESH_TOKEN_KEY"):(".+?"))""".toRegex() to """"$REFRESH_TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        /**
         * Creates an obfuscator for API key-related login requests.
         * It will replace the `"key":"<KEY>"` pattern with `"key":"***"`.
         */
        private fun createApiKey(): RegexPatternReplacer = mapOf(
            """(("$API_KEY_KEY"):(\s?".+?"))""".toRegex() to """"$API_KEY_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        /**
         * TODO
         */
        private fun loginApiKey(): RegexPatternReplacer = mapOf(
            """(("$API_KEY_KEY"):(\s?".+?"))""".toRegex() to """"$API_KEY_KEY":"***"""",
            """(("$ACCESS_TOKEN_KEY"):(".+?"))""".toRegex() to """"$ACCESS_TOKEN_KEY":"***"""",
            """(("$REFRESH_TOKEN_KEY"):(".+?"))""".toRegex() to """"$REFRESH_TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        /**
         * TODO
         */
        private fun loginApple(): RegexPatternReplacer = mapOf(
            """(("$ID_TOKEN_KEY"):(\s?".+?"))""".toRegex() to """"$ID_TOKEN_KEY":"***"""",
            """(("$ACCESS_TOKEN_KEY"):(".+?"))""".toRegex() to """"$ACCESS_TOKEN_KEY":"***"""",
            """(("$REFRESH_TOKEN_KEY"):(".+?"))""".toRegex() to """"$REFRESH_TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        /**
         * TODO
         */
        private fun loginFacebook(): RegexPatternReplacer = mapOf(
            """(("$FB_ACCESS_TOKEN_KEY"):(\s?".+?"))""".toRegex() to """"$FB_ACCESS_TOKEN_KEY":"***"""",
            """(("$ACCESS_TOKEN_KEY"):(".+?"))""".toRegex() to """"$ACCESS_TOKEN_KEY":"***"""",
            """(("$REFRESH_TOKEN_KEY"):(".+?"))""".toRegex() to """"$REFRESH_TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        /**
         * TODO
         */
        private fun loginGoogle(): RegexPatternReplacer = mapOf(
            """(("$AUTHCODE_KEY"):(\s?".+?"))""".toRegex() to """"$AUTHCODE_KEY":"***"""",
            """(("$ID_TOKEN_KEY"):(\s?".+?"))""".toRegex() to """"$ID_TOKEN_KEY":"***"""",
            """(("$ACCESS_TOKEN_KEY"):(".+?"))""".toRegex() to """"$ACCESS_TOKEN_KEY":"***"""",
            """(("$REFRESH_TOKEN_KEY"):(".+?"))""".toRegex() to """"$REFRESH_TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        /**
         * TODO
         */
        private fun loginJwt(): RegexPatternReplacer = mapOf(
            """(("$TOKEN_KEY"):(\s?".+?"))""".toRegex() to """"$TOKEN_KEY":"***"""",
            """(("$ACCESS_TOKEN_KEY"):(".+?"))""".toRegex() to """"$ACCESS_TOKEN_KEY":"***"""",
            """(("$REFRESH_TOKEN_KEY"):(".+?"))""".toRegex() to """"$REFRESH_TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        /**
         * TODO
         */
        private fun customFunction(): RegexPatternReplacer = CustomFunctionPatternReplacer
    }
}

internal class GenericRegexPatternReplacer(
    private val patternReplacementMap: Map<Regex, String>
) : RegexPatternReplacer {
    override fun findAndReplace(input: String): String {
        var obfuscatedString = input
        val entries: Set<Map.Entry<Regex, String>> = patternReplacementMap.entries
        for (entry in entries) {
            val pattern = entry.key
            obfuscatedString = pattern.replace(obfuscatedString, entry.value)
        }
        return obfuscatedString
    }
}

internal object CustomFunctionPatternReplacer : RegexPatternReplacer {
    override fun findAndReplace(input: String): String {
        val (pattern, replacement) = when {
            input.contains("REQUEST: ") ->
                """("arguments"):\[.*]""".toRegex() to """"arguments":[***]"""
            input.contains("RESPONSE: 200 OK") ->
                """BODY START\n.*\nBODY END""".toRegex() to "BODY START\n***\nBODY END"
            else -> return input
        }
        return pattern.replace(input, replacement)
    }
}
