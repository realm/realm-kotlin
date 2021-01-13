package io.realm
import kotlinx.coroutines.launch
class RealmTests {

    fun crud() {
        val config = RealmConfiguration.Builder().build()
        val realm: Realm = Realm.open(config)
        realm.create().apply {

        }
        realm.use {

        }
    }
}