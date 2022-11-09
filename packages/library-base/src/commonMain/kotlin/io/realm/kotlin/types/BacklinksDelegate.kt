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

package io.realm.kotlin.types

import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.query.RealmResults
import kotlin.reflect.KProperty

/**
 * Delegate for backlinks collections. Backlinks are used to establish reverse relationships
 * between Realm models.
 *
 * See [backlinks] on how to define inverse relationships in your model.
 */
public interface BacklinksDelegate<T : TypedRealmObject> {
    public operator fun getValue(
        reference: RealmObject,
        targetProperty: KProperty<*>
    ): RealmResults<T>
}
