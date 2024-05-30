package io.realm.example.kmmsample.androidApp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.realm.example.kmmsample.Calculator
import io.realm.example.kmmsample.Greeting
import kotlinx.coroutines.launch

fun greet(): String {
    return Greeting().greeting()
}

class MainActivity : AppCompatActivity() {

    private lateinit var countTV: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tv: TextView = findViewById(R.id.textView)
        tv.text = greet()

        val numATV: EditText = findViewById(R.id.editTextNumberDecimalA)
        val numBTV: EditText = findViewById(R.id.editTextNumberDecimalB)

        val sumTV: TextView = findViewById(R.id.textViewSum)
        countTV = findViewById(R.id.textHistoryCount)

        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                try {
                    val numA = Integer.parseInt(numATV.text.toString())
                    val numB = Integer.parseInt(numBTV.text.toString())
                    sumTV.text = "= " + Calculator.sum(numA, numB).toString()
                } catch (e: NumberFormatException) {
                    sumTV.text = "= 🤔"
                }
            }
        }

        countTV.text = "History count: ${Calculator.history().size}"

        numATV.addTextChangedListener(textWatcher)
        numBTV.addTextChangedListener(textWatcher)

        lifecycleScope.launch {
            Calculator.listen().collect {
                countTV.text = "History count: ${Calculator.history().size}"
            }
        }

        // Smoke testing that we can access fields using `@PersistedName` after obfuscation
        PersistedNameTest.testQueryByPersistedAndPublicName()
    }
}
