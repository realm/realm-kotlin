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

annotation class RealmObject

/**
 * This interface is added by the compiler plugin to all [RealmObject] annotated classes, it contains
 * internal properties of the model.
 *
 * This interface is not meant to be used externally (consider using [RealmModel] instead)
 */
@Suppress("VariableNaming")
interface RealmModelInternal : RealmModel {
    var `$realm$Pointer`: NativePointer?
    var `$realm$ObjectPointer`: NativePointer?
    var `$realm$TableName`: String?
    var `$realm$IsManaged`: Boolean
}
