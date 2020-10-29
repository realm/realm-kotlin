package io.realm

import io.realm.model.Person
import io.realm.runtimeapi.RealmModel
//import io.realm.model.PersonProxy
import kotlin.reflect.KClass

// TODO work around lack of reflection/codegen in Kotlin/Native, this should be solved by adding a compiler plugin
object TestUtils {
    fun factory(): (KClass<out RealmModel>) -> RealmModel {
        return {
            if (Person::class == it) Person()
            else error("Unsupported type: ${it.simpleName}")
        }
    }
}
