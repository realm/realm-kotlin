package io.realm.example.kmmsample

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpressionRepositoryTest {

    @Test
    fun insert() {
        val repository = ExpressionRepository()
        val expression = repository.addExpression("a + b")
        assertEquals("a + b", expression.expressionString)
    }
}
