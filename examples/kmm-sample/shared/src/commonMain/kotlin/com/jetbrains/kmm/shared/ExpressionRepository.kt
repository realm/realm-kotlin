package com.jetbrains.kmm.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.runtimeapi.RealmCompanion

class ExpressionRepository {

    val realm: Realm by lazy {
        val configuration = RealmConfiguration.Builder()
            // FIXME Remove when object creation is internalized
            .factory { _ -> Expression() }
            // FIXME Remove when shcema definition is internalized
            .classes(
                listOf(
                    Expression.Companion as RealmCompanion
                )
            )
            .build()

        Realm.open(configuration)
    }

    fun addExpression(expression: String) : Expression {
        realm.beginTransaction()
        val expression = realm.create(Expression::class).apply {
            string = expression
        }
        realm.commitTransaction()
        return expression
    }

}
