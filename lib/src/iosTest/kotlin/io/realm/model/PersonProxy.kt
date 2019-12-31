package io.realm.model

import realm.CInterop
import realm.RealmModel

class PersonProxy : Person() {
    override var name: String = ""
        get() {
            return if (isManaged) {
                return CInterop.objectGetString(objectPointer!!, "name")!!
            } else {
                field
            }
        }
        set(value) {
            // use the pointer to set the value
            if (isManaged) {
                CInterop.objectSetString(objectPointer!!, "name", value)
            } else {
                field = value
            }
        }

    override var age: Int = 0
        get() {
            return if (isManaged) {
                println("getting age managed")
                return CInterop.objectGetInt64(objectPointer!!, "age")?.toInt()!!
            } else {
                println("getting age unmanaged")
                field
            }
        }
        set(value) {
            // use the pointer to set the value
            if (isManaged) {
                println("Setting age to $value")
                CInterop.objectSetInt64(objectPointer!!, "age", value.toLong())
            } else {
                println("Unmanaged Setting age to $value")
                field = value
            }
        }

    override fun <T : RealmModel> newInstance(): T {
        return PersonProxy() as T //FIXME remove the cast
    }
}