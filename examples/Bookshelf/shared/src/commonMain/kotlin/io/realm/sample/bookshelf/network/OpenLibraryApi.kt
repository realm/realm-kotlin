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
package io.realm.sample.bookshelf.network

import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.*
import kotlinx.serialization.json.Json

class OpenLibraryApi {
    private val httpClient by lazy {
        HttpClient {
            install(JsonFeature) {
                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                serializer = KotlinxSerializer(json)
            }
        }
    }
    suspend fun findBook(title: String): SearchResult {
        return httpClient.get("$BOOK_SEARCH_ENDPOINT$title")
    }

    companion object {
        private const val BOOK_SEARCH_ENDPOINT = "https://openlibrary.org/search.json?title="
    }
}