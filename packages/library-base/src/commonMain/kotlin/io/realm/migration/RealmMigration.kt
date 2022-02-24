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

package io.realm.migration

/**
 * A base class for the various **realm migration** schemes.
 *
 * The migration scheme controls how schema and data is migrated when there are changes to the realm
 * object model.
 *
 * @see Configuration.SharedBuilder.migration
 * @see AutomaticSchemaMigration
 */
sealed interface RealmMigration

/**
 * An realm migration that performs automatic schema migration and allows additional custom
 * migration of data.
 */
fun interface AutomaticSchemaMigration : RealmMigration {
    /**
     * A **data migration context** providing access to the realm before and after an
     * [AutomaticSchemaMigration].
     */
    interface DataMigrationContext {

        /**
         * The realm before automatic schema migration.
         */
        val oldRealm: DynamicRealm
        /**
         * The realm after automatic schema migration.
         */
        val newRealm: DynamicMutableRealm

        /**
         * Convenience method to iterate all objects of a certain class from the realm before migration
         * with access to an updatable [DynamicMutableRealmObject] reference to the corresponding object in
         * the already migrated realm.
         *
         * @param className the name of the class for which to iterate all instances in the old realm.
         * @param block block of code that will be triggered for each instance of the class in the old
         * realm. The `newObject` will be a reference to the corresponding [DynamicMutableRealmObject] in
         * the already migrated realm, or null if the object has been deleted.
         */
        fun enumerate(className: String, block: (oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject?) -> Unit) {
            val find: RealmResults<out DynamicRealmObject> = oldRealm.query(className).find()
            find.forEach {
                block(it, newRealm.findLatest(it))
            }
        }
    }

    /**
     * Method triggered and allowing migration of data in the case that the schema of the realm has
     * changed.
     */
    fun migrate(migrationContext: DataMigrationContext)
}

// FIXME Should we eliminate these. Only for convenience to allow deconstruction in lambda { (oldRealm, newRealm) -> }
operator fun AutomaticSchemaMigration.DataMigrationContext.component1() = this.oldRealm
operator fun AutomaticSchemaMigration.DataMigrationContext.component2() = this.newRealm
