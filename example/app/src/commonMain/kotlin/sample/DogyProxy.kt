package sample
import realm.CInterop
import realm.RealmModel

class DogProxy : Dog() {
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
                return CInterop.objectGetInt64(objectPointer!!, "age")?.toInt()!!
            } else {
                field
            }
        }
        set(value) {
            // use the pointer to set the value
            if (isManaged) {
                CInterop.objectSetInt64(objectPointer!!, "age", value.toLong())
            } else {
                field = value
            }
        }

    override fun <T : RealmModel> newInstance(): T {
        return DogProxy() as T //FIXME remove the cast
    }
}





//package sample
//
//import kotlinx.cinterop.toKString
//import objectstore_wrapper.object_get_int64
//import objectstore_wrapper.object_get_string
//import objectstore_wrapper.object_set_int64
//import objectstore_wrapper.object_set_string
//
//class DogProxy : Dog() {
//    override var name: String = ""
//        get() {
//            return if (isManaged) {
//                return object_get_string(objectPointer, "name")?.toKString()!!
//            } else {
//                field
//            }
//        }
//        set(value) {
//            // use the pointer to set the value
//            if (isManaged) {
//                object_set_string(objectPointer, "name", value)
//            } else {
//                field = value
//            }
//        }
//
//    override var age: Int = 0
//        get() {
//            return if (isManaged) {
//                return object_get_int64(objectPointer, "age").toInt()
//            } else {
//                field
//            }
//        }
//        set(value) {
//            // use the pointer to set the value
//            if (isManaged) {
//                object_set_int64(objectPointer, "age", value.toLong())
//            } else {
//                field = value
//            }
//        }
//
//}