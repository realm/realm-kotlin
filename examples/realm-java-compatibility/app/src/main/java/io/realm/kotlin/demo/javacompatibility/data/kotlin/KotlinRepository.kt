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

package io.realm.kotlin.demo.javacompatibility.data.kotlin

import android.util.Log
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.demo.javacompatibility.data.BOOKS_SIZE
import io.realm.kotlin.demo.javacompatibility.data.METADATA_SIZE
import io.realm.kotlin.demo.javacompatibility.data.METADATA_VALUE_SIZE
import io.realm.kotlin.demo.javacompatibility.data.REPEAT_TEST
import io.realm.kotlin.demo.javacompatibility.data.Repository
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlin.system.measureTimeMillis

class BookRealm: RealmObject {
    @PrimaryKey var idRealm: String = ""
    var metas: RealmList<MetadataRealm> = realmListOf()
}

class MetadataRealm: EmbeddedRealmObject {
    var key: String = ""
    var values: RealmList<String?> = realmListOf()
}


class KotlinRepository: Repository {

    val realm: Realm

    init {
        val config = RealmConfiguration.Builder(setOf(BookRealm::class, MetadataRealm::class))
            .name("kotlin.realm")
            .build()
        Realm.deleteRealm(config)
        realm = Realm.open(config)
    }

    override fun createData() {
        realm.writeBlocking {
            repeat(BOOKS_SIZE) { bookNo ->
                val book = BookRealm().apply {
                    idRealm = "book-$bookNo"
                    metas = realmListOf()
                    repeat(METADATA_SIZE) { metadataNo ->
                        val metadata = MetadataRealm().apply {
                            key = "metadata-$bookNo-$metadataNo"
                            values = realmListOf()
                            repeat(METADATA_VALUE_SIZE) { metadataValueNo ->
                                values.add("metadata-value-$bookNo-$metadataNo-$metadataValueNo")
                            }
                        }
                        metas.add(metadata)
                    }
                }
                copyToRealm(book)
            }
        }
    }

    override fun queryAndConvertData() {
        Log.e("REALM-BENCHMARK", "Starting Kotlin test")
        val results = mutableListOf<Long>()
        val mapped = mutableListOf<BookRealm>()
        repeat(REPEAT_TEST) {
            measureTimeMillis {
                realm.query(BookRealm::class).find().map { results ->
                    mapped.add(results.copyFromRealm())
                }
            }.let { measurement ->
                results.add(measurement)
            }
        }
        printResults(results)
    }

    override fun close() {
        realm.close()
    }
}


