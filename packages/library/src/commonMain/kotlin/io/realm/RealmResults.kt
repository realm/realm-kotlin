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

package io.realm

import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KClass

class RealmResults<T : RealmModel> constructor(
    private val queryPointer: NativePointer,
    private val clazz: KClass<T>,
    private val modelFactory: ModelFactory
) : AbstractList<T>() {
    override val size: Int
        get() = TODO() // CInterop.queryGetSize(queryPointer).toInt()

    override fun get(index: Int): T {
        val model = modelFactory.invoke(clazz) as RealmModelInternal
//        val objectPointer = TODO() // CInterop.queryGetObjectAt(queryPointer, clazz.simpleName!!, index)
//        model.isManaged = true
//        model.realmObjectPointer = objectPointer
//        model.tableName = clazz.simpleName
        // call T factory to instantiate an Object of type T using it's pointer 'objectPointer'
        return model as T
    }
}
