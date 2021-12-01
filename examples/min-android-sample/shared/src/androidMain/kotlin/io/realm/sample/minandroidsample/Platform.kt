package io.realm.sample.minandroidsample

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject

class Sample: RealmObject {
    var name: String = ""
}

actual class Platform actual constructor() {
    val config = RealmConfiguration.with(schema = setOf(Sample::class))
    val realm = Realm.open(config)
    actual val platform: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}