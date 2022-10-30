/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.kotlin.entities.backlink

import io.realm.kotlin.ext.linkingObjects
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.Ignore
import org.mongodb.kbson.ObjectId

class Child : RealmObject {
    val parents by linkingObjects(Parent::child)
    val parentsByList by linkingObjects(Parent::childList)
    val parentsBySet by linkingObjects(Parent::childSet)
}

class EmbeddedChild : EmbeddedRealmObject {
    var id = ObjectId()
    var parent: Parent? = null
}

class Parent(var id: Int) : RealmObject {
    constructor() : this(0)

    var child: Child? = null
    var childList: RealmList<Child> = realmListOf()
    var childSet: RealmSet<Child> = realmSetOf()

    var embeddedChild: EmbeddedChild? = EmbeddedChild()
    val embeddedChildren by linkingObjects(EmbeddedChild::parent)
}

class Recursive : RealmObject {
    var name: RealmUUID = RealmUUID.random()
    var uuidSet: RealmSet<RealmUUID> = realmSetOf()
    var uuidList: RealmList<RealmUUID> = realmListOf()

    var recursiveField: Recursive? = null
    val references by linkingObjects(Recursive::recursiveField)
}

class MissingSourceProperty : RealmObject {
    @Ignore
    var reference: MissingSourceProperty? = null
    val references by linkingObjects(MissingSourceProperty::reference)
}
