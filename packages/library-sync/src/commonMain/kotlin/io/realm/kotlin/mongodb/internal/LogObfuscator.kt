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

import io.realm.kotlin.mongodb.HttpLogObfuscator
import io.realm.kotlin.mongodb.internal.LogReplacer.Companion.defaultFeatureToReplacerMap

// Replaces any given regex pattern present in a given logger message matching a number of
// operations/features: register user, login with email and password, login with tokens and run
// custom functions with parameters
internal interface LogReplacer {

    // Uses a replacer to obfuscate (or not) a given logger message
    fun findAndReplace(input: String): String

    companion object {

        // Defines the first part of the URL path to a feature
        private const val AUTH = "auth"
        internal const val FUNCTIONS = "functions/call"

        // Credentials feature
        private const val CREDENTIALS_PROVIDER = "$AUTH/providers"

        // Credentials type
        private const val PROVIDER_EMAIL_PASSWORD = "$CREDENTIALS_PROVIDER/local-userpass"
        private const val PROVIDER_API_KEY = "$CREDENTIALS_PROVIDER/api-key"
        private const val PROVIDER_APPLE = "$CREDENTIALS_PROVIDER/oauth2-apple"
        private const val PROVIDER_FACEBOOK = "$CREDENTIALS_PROVIDER/oauth2-facebook"
        private const val PROVIDER_GOOGLE = "$CREDENTIALS_PROVIDER/oauth2-google"
        private const val PROVIDER_JWT = "$CREDENTIALS_PROVIDER/custom-token"

        // Email password provider operations
        internal const val EMAIL_PASSWORD_REGISTER = "$PROVIDER_EMAIL_PASSWORD/register"
        internal const val EMAIL_PASSWORD_LOGIN = "$PROVIDER_EMAIL_PASSWORD/login"

        // API key operations
        internal const val API_KEY_REGISTER = "$AUTH/api_keys" // Key creation uses a different path
        internal const val API_KEY_LOGIN = "$PROVIDER_API_KEY/login"

        // Apple token operations
        internal const val APPLE_LOGIN = "$PROVIDER_APPLE/login"

        // Facebook token operations
        internal const val FACEBOOK_LOGIN = "$PROVIDER_FACEBOOK/login"

        // Google token operations
        internal const val GOOGLE_LOGIN = "$PROVIDER_GOOGLE/login"

        // JWT operations
        internal const val JWT_LOGIN = "$PROVIDER_JWT/login"

        // Keys to be replaced by the replacer
        private const val API_KEY_KEY = "key"
        private const val PASSWORD_KEY = "password"
        private const val AUTHCODE_KEY = "authCode"
        private const val ID_TOKEN_KEY = "id_token"
        private const val TOKEN_KEY = "token"
        private const val FB_ACCESS_TOKEN_KEY = "accessToken"

        // Map of default feature operations to replacers
        val defaultFeatureToReplacerMap: Map<String, LogReplacer> = mapOf(
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

        // Patterns used when sending a register mail request:
        // `"password":"<PASSWORD>"` becomes `"password":"***"`
        private fun registerEmailPassword(): LogReplacer = mapOf(
            """(("$PASSWORD_KEY"):(".+?"))""".toRegex() to """"$PASSWORD_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        // Patterns used when sending a login with mail request:
        // `"password":"<PASSWORD>"` becomes `"password":"***"`
        private fun loginEmailPassword(): LogReplacer = mapOf(
            """(("$PASSWORD_KEY"):(".+?"))""".toRegex() to """"$PASSWORD_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        // Patterns used when sending a create API key request:
        // `"key":"<KEY>"` becomes `"key":"***"`
        private fun createApiKey(): LogReplacer = mapOf(
            """(("$API_KEY_KEY"):(\s?".+?"))""".toRegex() to """"$API_KEY_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        // Patterns used when sending a login with API key request:
        // `"key":"<KEY>"` becomes `"key":"***"`
        private fun loginApiKey(): LogReplacer = mapOf(
            """(("$API_KEY_KEY"):(\s?".+?"))""".toRegex() to """"$API_KEY_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        // Patterns used when sending a login with an Apple token request:
        // `"id_token":"<TOKEN>"` becomes `"id_token":"***"`
        private fun loginApple(): LogReplacer = mapOf(
            """(("$ID_TOKEN_KEY"):(\s?".+?"))""".toRegex() to """"$ID_TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        // Patterns used when sending a login with a Facebook token request:
        // `"accessToken":"<TOKEN>"` becomes `"accessToken":"***"`
        private fun loginFacebook(): LogReplacer = mapOf(
            """(("$FB_ACCESS_TOKEN_KEY"):(\s?".+?"))""".toRegex() to """"$FB_ACCESS_TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        // Patterns used when sending a login with a Google token request:
        // `"authCode":"<TOKEN>"` becomes `"authCode":"***"`
        // `"id_token":"<TOKEN>"` becomes `"id_token":"***"`
        private fun loginGoogle(): LogReplacer = mapOf(
            """(("$AUTHCODE_KEY"):(\s?".+?"))""".toRegex() to """"$AUTHCODE_KEY":"***"""",
            """(("$ID_TOKEN_KEY"):(\s?".+?"))""".toRegex() to """"$ID_TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        // Patterns used when sending a login with a JWT request:
        // `"token":"<TOKEN>"` becomes `"token":"***"`
        private fun loginJwt(): LogReplacer = mapOf(
            """(("$TOKEN_KEY"):(\s?".+?"))""".toRegex() to """"$TOKEN_KEY":"***""""
        ).let {
            GenericRegexPatternReplacer(it)
        }

        // Creates a replacer that hides parameters for custom functions
        private fun customFunction(): LogReplacer = CustomFunctionPatternReplacer
    }
}

// Replacer for any given feature but custom functions
// FIXME Access and refresh tokens cannot be replaced by regex due to
//  https://github.com/realm/realm-kotlin/issues/1284
//  so until we figure out what is wrong, do manual string replacement and hopefully nothing will
//  change - if the message string changes our tests will hopefully catch it
internal class GenericRegexPatternReplacer(
    private val patternReplacementMap: Map<Regex, String>
) : LogReplacer {
    override fun findAndReplace(input: String): String {
        return if (
            input.contains("RESPONSE: 200 OK") &&
            input.contains("access_token") &&
            input.contains("refresh_token")
        ) {
            val beforeAccessToken = input.substringBefore(""""access_token"""")
            val afterRefreshToken = input.substringAfter(""""user_id":""")
            """$beforeAccessToken"access_token":"***","refresh_token":"***","user_id":$afterRefreshToken"""
        } else {
            var obfuscatedString = input
            val entries: Set<Map.Entry<Regex, String>> = patternReplacementMap.entries
            for (entry in entries) {
                val pattern = entry.key
                obfuscatedString = pattern.replace(obfuscatedString, entry.value)
            }
            obfuscatedString
        }
    }
}

// Replacer for custom function arguments. It combs the request and hides both the parameters that
// are send and any possible received result
internal object CustomFunctionPatternReplacer : LogReplacer {
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

internal object LogObfuscatorImpl : HttpLogObfuscator {

    private val urlRegex =
        Regex("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()!@:%_\\+.~#?&\\/\\/=]*)")

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
    )

    private val regexReplacerMap: Map<String, LogReplacer> = defaultFeatureToReplacerMap

    override fun obfuscate(input: String): String {
        features.forEach { feature ->
            urlRegex.find(input)?.let { matchResult ->
                val url = matchResult.value
                if (url.contains(feature.urlPath)) {
                    val patternReplacer = regexReplacerMap[feature.urlPath]
                    return patternReplacer?.findAndReplace(input) ?: input
                }
            }
        }
        return input
    }
}

// Features that will be obfuscated by default. It uses the `urlPath` to tell the replacer when to
// find the patterns in the logger
internal enum class ObfuscatorFeature(internal val urlPath: String) {
    FEATURE_EMAIL_REGISTER(LogReplacer.EMAIL_PASSWORD_REGISTER),
    FEATURE_EMAIL_LOGIN(LogReplacer.EMAIL_PASSWORD_LOGIN),
    FEATURE_API_KEY_CREATE(LogReplacer.API_KEY_REGISTER),
    FEATURE_API_KEY_LOGIN(LogReplacer.API_KEY_LOGIN),
    FEATURE_APPLE_LOGIN(LogReplacer.APPLE_LOGIN),
    FEATURE_FACEBOOK_LOGIN(LogReplacer.FACEBOOK_LOGIN),
    FEATURE_GOOGLE_LOGIN(LogReplacer.GOOGLE_LOGIN),
    FEATURE_JWT_LOGIN(LogReplacer.JWT_LOGIN),
    FEATURE_CUSTOM_FUNCTION_REQUEST(LogReplacer.FUNCTIONS)
}
