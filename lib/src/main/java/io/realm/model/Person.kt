package io.realm.model

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.internal.BaseRealm
import io.realm.internal.ColumnIndices
import io.realm.internal.Row


// One interface to inherit from
// Final model classes supported
class Person : RealmObject {

    // Annotations supported as normal
    // `val` supported on Primary keys
    @PrimaryKey val name: String = ""
    var age: Int = 0

    // Links supported as normal
    var otherDog: Dog? = null
    var list: RealmList<Dog> = RealmList()

    // lateinit supported
    lateinit var dog: Dog
}

// Data classes supported
data class Dog(@PrimaryKey val name: String, var age: Int) : RealmObject


// - Add implementation required to match properties defined in [RealmProxy]
// - Add companion object with implementation
// - Replace getters and setters (
class PersonSourceCompilerStep : RealmObject {
    internal var realm: BaseRealm? = null
    internal var row: Row? = null

    /**
     * Internal companion object should contain must of the methods from the old proxy class
     * Same approach is used. Internal methods are added that match the methods defined in [ProxyHelperMethods]
     *
     * Since this is only static methods, we could also consider moving it to an outside helper class.
     */
    internal companion object{
        internal lateinit var columns: ColumnIndices
        internal fun insert(obj: Person, realm: BaseRealm) { TODO("missing impl") }
        // ...
    }

    // User defined properties and methods
    var age: Int = 0
        // These are added by the Compiler plugin
        get() {
            val row = row
            return row?.getInt(columns) ?: field
        }
        set(value) {
            val row = row
            if (row != null) {
                row.setInt(columns.columnAge, value)
            } else {
                field = value
            }
        }
}


// Step 1: Modify model classes at the source level
// - Add implementation required to match properties defined in [RealmProxy]
// - Add companion object with implementation
class PersonASTModified : RealmObject {
    internal var realm: BaseRealm? = null
    internal var row: Row? = null

    /**
     * Internal companion object should contain must of the methods from the old proxy class
     * Same approach is used. Internal methods are added that match the methods defined in [ProxyHelperMethods]
     *
     * Since this is only static methods, we could also consider moving it to an outside helper class.
     *
     * It is also unclear what happens if the user defined their own companion object. It is not
     * possible to define two companion objects with different visibility. This might expose
     * internal Realm behavior.
     */
    internal companion object{
        internal lateinit var columns: ColumnIndices
        internal fun insert(obj: Person, realm: BaseRealm) { TODO("missing impl") }
        // ...
    }

    // User defined properties and methods
    var age: Int = 0
        // These are added by the Compiler plugin
        get() {
            val row = row
            return row?.getInt(columns) ?: field
        }
        set(value) {
            val row = row
            if (row != null) {
                row.setInt(columns.columnAge, value)
            } else {
                field = value
            }
        }
}
