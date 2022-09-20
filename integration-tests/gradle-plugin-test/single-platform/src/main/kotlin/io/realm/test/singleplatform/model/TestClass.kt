package io.realm.test.singleplatform.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class TestClass : RealmObject {
    @PrimaryKey
    var id: Long = 0

    var text: String = "INIT"
}