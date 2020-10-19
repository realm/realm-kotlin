package io.realm

import kotlinx.cinterop.*
import realm_wrapper.*
import kotlin.test.Test

class CinteropTest {

    // FIXME Testing basic C API wrapper interaction (like in AndroidTest's CinteropTest)
    @Test
    fun cinterop_cinterop() {

        println(realm_get_library_version()!!.toKString())

        // FIXME Seems to miss some symbols when included ... maybe are more of the libraries realm.def or get Simon to supply full consumables
        val config = realm_config_new()

        // FIXME Haven't figured out how initilialize full schema yet...
        //  relevant links
        //   - https://kotlinlang.org/docs/reference/native/c_interop.html#passing-and-receiving-structs-by-value
        //   - https://kotlinlang.org/docs/reference/native/c_interop.html#passing-pointers-to-bindings
        //   - https://kotlinlang.org/docs/reference/native/c_interop.html#scope-local-pointers
//        memScoped { memScope
//            val config = realm_config_new()
//
////            // Setting path
//            val cpath: realm_string = alloc()
//            val path = "testfile".cstr
//            cpath.data = path.getPointer(memScope)
//            cpath.size = path.size.toULong()
//            realm_config_set_path(config, cpath.readValue())
//
//            val class1: realm_class_info = alloc<realm_class_info>()
//            val class1 = alloc.apply {
//                name = "foo" // FIXME Needs to be realm_string_t
//                primary_key = alloc<realm_string>().apply { data = "".cstr.getPointer(memScope) }
//            }
//            }
    }
}
