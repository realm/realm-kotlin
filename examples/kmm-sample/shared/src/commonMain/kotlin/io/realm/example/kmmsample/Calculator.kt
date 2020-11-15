package io.realm.example.kmmsample

class Calculator private constructor() {
    companion object {

        val repository: ExpressionRepository by lazy { ExpressionRepository() }

        fun sum(a: Int, b: Int): Int {
            repository.addExpression("$a + $b")
            return a + b
        }
    }
}
