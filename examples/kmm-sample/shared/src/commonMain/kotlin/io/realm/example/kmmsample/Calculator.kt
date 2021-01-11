/*
 * Copyright 2020 JetBrains s.r.o.
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

import io.realm.Registration

class Calculator private constructor() {
    companion object {

        private val repository: ExpressionRepository by lazy { ExpressionRepository() }

        fun sum(a: Int, b: Int): Int {
            repository.addExpression("$a + $b")
            return a + b
        }

        fun listen(block: () -> Unit): Registration {
            return this.repository.listen(block)
        }

        fun history(): List<Expression> {
            return repository.expressions()
        }
    }
}
