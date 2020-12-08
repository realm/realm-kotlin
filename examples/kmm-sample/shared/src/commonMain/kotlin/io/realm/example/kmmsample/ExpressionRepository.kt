/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.example.kmmsample

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.runtimeapi.RealmCompanion

class ExpressionRepository {

    val realm: Realm by lazy {
        val configuration = RealmConfiguration.Builder()
            // FIXME MEDIATOR Remove when object creation is internalized
            //  https://github.com/realm/realm-kotlin/issues/54
            .factory { _ -> Expression() }
            // FIXME MEDIATOR Remove when shcema definition is internalized
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
