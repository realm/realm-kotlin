//package io.realm
//
//import io.realm.util.Module
//import test.Sample
//import kotlin.test.Test
//import kotlin.test.assertEquals
//
//class ModuleTest {
//
//    @Test
//    fun schema() {
//        val moduleSchema = Module(listOf(Sample::class)).schema()
//        assertEquals(
//                listOf(Sample.schema()).joinToString(prefix = "[", separator = ",", postfix = "]") { it },
//                moduleSchema
//        )
//    }
//}
