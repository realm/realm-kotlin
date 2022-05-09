package io.realm.entities.embedded

import io.realm.RealmObject

class EmbeddedChildWithInitializer : RealmObject {

    var child: EmbeddedChild? = EmbeddedChild("Initial child")
}
