package io.realm

import io.realm.model.Person

expect object TestUtils {
    fun realmDefaultDirectory(): String
    fun getPerson(): Person
}