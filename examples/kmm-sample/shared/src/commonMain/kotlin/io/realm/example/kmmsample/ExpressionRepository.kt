package io.realm.example.kmmsample

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.runtimeapi.RealmCompanion

class ExpressionRepository {

    val realm: Realm by lazy {
        val configuration = RealmConfiguration.Builder()
            // FIXME Remove when object creation is internalized
            //  https://github.com/realm/realm-kotlin/issues/54
            .factory { _ -> Expression() }
            // FIXME Remove when shcema definition is internalized
            //  https://github.com/realm/realm-kotlin/issues/54
            .classes(
                listOf(
                    Expression.Companion as RealmCompanion
                )
            )
            .build()

        Realm.open(configuration)
    }

    fun addExpression(expression: String): Expression {
        realm.beginTransaction()
        val expression = realm.create(Expression::class).apply {
            expressionString = expression
        }
        realm.commitTransaction()
        return expression
    }
}
