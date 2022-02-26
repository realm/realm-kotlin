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

import io.realm.RealmResults
import io.realm.dynamic.DynamicMutableRealm
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.dynamic.DynamicRealm
import io.realm.dynamic.DynamicRealmObject

/**
 * A base class for the various **realm migration** schemes.
 *
 * The migration scheme controls how schema and data is migrated when there are changes to the realm
 * object model.
 *
 * @see Configuration.SharedBuilder.migration
 * @see AutomaticSchemaMigration
 */
public sealed interface RealmMigration

/**
 * An realm migration that performs automatic schema migration and allows additional custom
 * migration of data.
 *
 * The automatic schema migration will not change data for objects and properties that have not
 * been affected by the migration. But properties that have changed configuration (name or type)
 * will be initialized with default values in the migrated realm and data has to be moved manually.
 * The [migrate] callback provides access to the previous and the migrated realm through a dynamic
 * (string based) API that allow such transformations. Examples are:
 * - Merging, transforming and splitting property values
 * - Renaming a property
 * - Changing type of a property
 *
 * Transformation like these can easily be done through [MigrationContext.enumerate] that iterates
 * all objects of a certain type and provides access to the old and new instance of an object. Some
 * example are given in the documentation of [MigrationContext.enumerate].
 */
public fun interface AutomaticSchemaMigration : RealmMigration {
    /**
     * A **data migration context** providing access to the realm before and after an
     * [AutomaticSchemaMigration].
     *
     * *NOTE:* All objects obtained from `oldRealm` and `newRealm` are only valid in the scope of
     * the migration.
     */
    public interface MigrationContext {

        /**
         * The realm before automatic schema migration.
         */
        public val oldRealm: DynamicRealm

        /**
         * The realm after automatic schema migration.
         */
        public val newRealm: DynamicMutableRealm

        /**
         * Convenience method to iterate all objects of a certain class from the realm before migration
         * with access to an updatable [DynamicMutableRealmObject] reference to the corresponding object in
         * the already migrated realm. This makes it possible to do more advanced data mapping like merging
         * or splitting field data or moving data while changing the type.
         *
         * Some common scenarios are shown below:
         *
         * ```
         * // Old data model
         * class MigrationSample: RealmObject {
         *     var firstName: String = "First"
         *     var lastName: String = "Last"
         *     var property: String = "Realm"
         *     var type: Int = 42
         * }
         *
         * // New data model
         * class MigrationSample: RealmObject {
         *     var fullName: String = "First Last"
         *     var renamedProperty: String = "Realm"
         *     var type: String = "42"
         * }
         *
         * migrationContext.enumerate("MigrationSample") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
         *     newObject?.run {
         *         // Merge property
         *         set( "fullName", "${oldObject.getValue<String>("firstName")} ${ oldObject.getValue<String>("lastName") }" )
         *
         *         // Rename property
         *         set("renamedProperty", oldObject.getValue<String>("property"))
         *
         *         // Change type
         *         set("type", oldObject.getValue<Long>("type").toString())
         *     }
         * }
         * ```
         *
         * @param className the name of the class for which to iterate all instances in the old realm.
         * @param block block of code that will be triggered for each instance of the class in the old
         * realm. The `newObject` will be a reference to the corresponding [DynamicMutableRealmObject] in
         * the already migrated realm, or null if the object has been deleted.
         */
        public fun enumerate(
            className: String,
            block: (oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject?) -> Unit
        ) {
            val find: RealmResults<out DynamicRealmObject> = oldRealm.query(className).find()
            find.forEach {
                // TODO OPTIMIZE Using find latest on every object is inefficient
                block(it, newRealm.findLatest(it))
            }
        }
    }

    /**
     * Method called when the schema of the realm has changed.
     *
     * The schema has automatically been migrated when this callback is triggered, but any data
     * must be manually moved using the migration context.
     *
     * @param migrationContext migration context giving access to the old and new realms.
     * */
    public fun migrate(migrationContext: MigrationContext)
}
