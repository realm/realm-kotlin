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

package io.realm.runtimeapi

interface RealmCompanion {
    // TODO Should be properly types i.e. io.realm.interop.Table, which require it to be in the cinterop module
    fun `$realm$schema`(): String // TODO change to use cinterop Table class instead or a marker interface that Table will be implementing
    fun `$realm$newInstance`(): Any
    // TODO Consider adding additional methods
    // TODO Consider adding type parameter for the class
}
