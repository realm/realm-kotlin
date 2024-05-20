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

package io.realm.kotlin.types.annotations

import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
@MustBeDocumented
/**
 * Annotation marking a field as indexed.
 *
 * Multiple fields in a RealmObject class can have this annotation.
 *
 * This annotation applies to the following primitive types: [String], [Boolean], [Byte], [Char],
 * [Short], [Int], [Long], [RealmInstant], [ObjectId], [BsonObjectId], [RealmUUID] as well as their
 * nullable variants.
 *
 * This annotation cannot be combined with [FullText].
 */
public annotation class Index
