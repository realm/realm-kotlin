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
import kotlinx.coroutines.flow.Flow

class ExpressionRepository {

    val realm: Realm by lazy {
        val configuration = RealmConfiguration.defaultConfig(schema = setOf(Expression::class))
        Realm.openBlocking(configuration)
    }

    fun addExpression(expression: String): Expression {
        return realm.writeBlocking {
            copyToRealm(Expression().apply { expressionString = expression })
        }
    }

    fun expressions(): List<Expression> {
        return realm.objects(Expression::class)
    }

    fun observeChanges(): Flow<List<Expression>> {
        return realm.objects(Expression::class).observe()
    }
}
