// package io.realm.util
//
// import io.realm.runtimeapi.RealmCompanion
// import io.realm.runtimeapi.RealmModel
// import kotlin.reflect.KClass
//
// /**
// * Interim helper to place manual code written around schemas.
// */
// class Module(classes: List<KClass<out RealmModel>>) {
//    val classes: Collection<KClass<out RealmModel>> = HashSet(classes.toSet())
//
//    fun schema(): String {
//        return classes
//                .map { it.companion() }
//                .map { it.schema() }
//                .joinToString(prefix = "[", separator = ",", postfix = "]") { it }
//    }
//
//    private fun <T : Any> KClass<T>.companion(): RealmCompanion {
//        return this.nestedClasses.first { it.isCompanion }.objectInstance as RealmCompanion
//    }
//
// }
