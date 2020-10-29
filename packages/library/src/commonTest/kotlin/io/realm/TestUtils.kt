package io.realm

import io.realm.model.Person
import kotlin.reflect.KClass

// TODO work around lack of reflection/codegen in Kotlin/Native, this should be solved by adding a compiler plugin
object TestUtils {
    fun getPerson(): Person {
//        return PersonProxy()
        return Person()
    }

    fun factory(): (KClass<out RealmModel>) -> RealmModel {
        return {
//            if (Person::class == it) PersonProxy()
            if (Person::class == it) Person()
            else error("Unsupported type: ${it.simpleName}")
        }
    }
}
