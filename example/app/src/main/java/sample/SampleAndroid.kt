package sample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnAddPerson).setOnClickListener {
            val name = findViewById<TextView>(R.id.txtName).text.toString()
            val age= findViewById<TextView>(R.id.txtAge).text.toString().toInt()

            PersonRepository.addPerson(name, age)

            displayCounts()
        }

        displayCounts()
    }

    fun displayCounts() {
        findViewById<TextView>(R.id.txtNumberAdults).text = "${PersonRepository.adultsCount()} 👴👵"
        findViewById<TextView>(R.id.txtNumberMinors).text = "${PersonRepository.minorsCount()} 👦👶"
    }
}