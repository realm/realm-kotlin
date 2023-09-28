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

package io.realm.kotlin.demo.javacompatibility.data.java

import android.util.Log
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import io.realm.kotlin.demo.javacompatibility.data.BOOKS_SIZE
import io.realm.kotlin.demo.javacompatibility.data.METADATA_SIZE
import io.realm.kotlin.demo.javacompatibility.data.METADATA_VALUE_SIZE
import io.realm.kotlin.demo.javacompatibility.data.REPEAT_TEST
import io.realm.kotlin.demo.javacompatibility.data.Repository
import kotlin.system.measureTimeMillis

@RealmClass
open class BookRealm: io.realm.RealmObject() {
    @PrimaryKey
    var idRealm: String = ""
    var metas: RealmList<MetadataRealm> = RealmList()
}

@RealmClass(embedded = true)
open class MetadataRealm: io.realm.RealmObject() {
    var key: String = ""
    var values: RealmList<String?> = RealmList()
}

class JavaRepository: Repository {

    val realm: Realm

    init {
        val config = RealmConfiguration.Builder()
            .name("java.realm")
            .allowQueriesOnUiThread(true)
            .allowWritesOnUiThread(true)
            .build()
        Realm.deleteRealm(config)
        realm = Realm.getInstance(config)
    }

    override fun createData() {
        realm.executeTransaction {
            repeat(BOOKS_SIZE) { bookNo ->
                val book = BookRealm().apply {
                    idRealm = "book-$bookNo"
                    metas = RealmList()
                    repeat(METADATA_SIZE) { metadataNo ->
                        val metadata = MetadataRealm().apply {
                            key = "metadata-$bookNo-$metadataNo"
                            values = RealmList()
                            repeat(METADATA_VALUE_SIZE) { metadataValueNo ->
                                values.add("metadata-value-$bookNo-$metadataNo-$metadataValueNo")
                            }
                        }
                        metas.add(metadata)
                    }
                }
                it.copyToRealm(book)
            }
        }
    }

    override fun queryAndConvertData() {
        Log.e("REALM-BENCHMARK", "Starting Java Test")
        val results = mutableListOf<Long>()
        val mapped = mutableListOf<BookRealm>()
        repeat(REPEAT_TEST) {
            measureTimeMillis {
                realm.where(BookRealm::class.java).findAll().map { results: BookRealm ->
                    mapped.add(realm.copyFromRealm(results))
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
