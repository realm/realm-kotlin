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

package io.realm

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
@MustBeDocumented
/**
 * Annotation marking a field as a primary key inside Realm.
 *
 * Only one field in a RealmObject class can have this annotation, and the field should uniquely
 * identify the object.
 *
 * This annotation applies to the following primitive types: String, Byte, Char,
 * Short, Int and Long, as well as their nullable variants.
 */
annotation class PrimaryKey
