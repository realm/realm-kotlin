package io.realm.example.minandroidsample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject

class Sample: RealmObject {
    var name: String = ""
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val config = RealmConfiguration.with(schema = setOf(Sample::class))
        val realm = Realm.open(config)
    }
}