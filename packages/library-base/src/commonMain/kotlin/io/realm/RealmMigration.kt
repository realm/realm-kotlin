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

package io.realm

sealed interface RealmMigration
// FIXME DOC
// FIXME Should this be inner class of AutomaticSchemaMigration?
interface DataMigrationContext {
    val oldRealm : DynamicRealm
    val newRealm : DynamicMutableRealm
}

fun interface AutomaticSchemaMigration: RealmMigration {
    fun migrate(migrationContext: DataMigrationContext)
}
// FIXME Only for convenience to allow deconstruction in lambda { (oldRealm, newRealm) -> }
operator fun DataMigrationContext.component1() = this.oldRealm
operator fun DataMigrationContext.component2() = this.newRealm
fun DataMigrationContext.enumerate(className: String, block: (oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject) -> Unit) {
    val find: RealmResults<out DynamicRealmObject> = oldRealm.query(className).find()
    find.forEach {
        // FIXME In which cases can we fail to resolv the object??
        block(it, newRealm.findLatest(it) ?: error("Couldn't find object after migration"))
    }
}

// TODO
//fun interface ManualRealmMigration : Migration {
//    fun migrate(migrationContext: FullMigrationContext )
//}

