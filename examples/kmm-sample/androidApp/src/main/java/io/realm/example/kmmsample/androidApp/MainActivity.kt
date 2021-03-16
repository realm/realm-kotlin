package io.realm.example.kmmsample.androidApp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.realm.Cancellable
import io.realm.example.kmmsample.Calculator
import io.realm.example.kmmsample.Greeting

fun greet(): String {
    return Greeting().greeting()
}

class MainActivity : AppCompatActivity() {

    private lateinit var countTV: TextView
    private lateinit var registration: Cancellable

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

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        countTV.text = "History count: ${Calculator.history().size}"

        numATV.addTextChangedListener(textWatcher)
        numBTV.addTextChangedListener(textWatcher)
    }

    override fun onResume() {
        super.onResume()
        registration = Calculator.listen {
            countTV.text = "History count: ${Calculator.history().size}"
        }
    }

    override fun onPause() {
        super.onPause()
        registration.cancel()
    }
}
