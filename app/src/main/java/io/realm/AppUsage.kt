package io.realm

import io.realm.model.PersonSourceCompilerStep
import io.realm.usage.Person

class AppUsage {

    fun interactWithModelClass() {
        val realm = Realm.getDefaultInstance()
        val obj = PersonSourceCompilerStep()
    }
}