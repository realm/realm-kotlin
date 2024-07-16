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

package io.realm.kotlin

import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject

public interface RealmResource {
    public fun close() {}
}

public interface ResourceScope {
    public fun <T : BaseRealmObject> query(): RealmQuery<T>
    public operator fun invoke(block: ResourceScope.() -> Unit) {
        this.block()
    }
}

public interface ScopedResource {
    public operator fun invoke(block: ResourceScope.() -> Unit) {

    }

}


public fun <T, R> RealmResults<T>.mapAndRelease(block: (T) -> R): List<R>
        where T : BaseRealmObject,
              T : RealmResource
{
    return map {
        val ret = block(it)
                it.close()
        ret
    }
}
