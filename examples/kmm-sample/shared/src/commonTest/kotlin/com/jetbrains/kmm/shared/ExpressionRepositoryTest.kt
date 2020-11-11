package com.jetbrains.kmm.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpressionRepositoryTest {

    @Test
    fun insert() {
        val repository = ExpressionRepository()
        val expression = repository.addExpression("a + b")
        assertEquals("a + b", expression.string)
    }
}
