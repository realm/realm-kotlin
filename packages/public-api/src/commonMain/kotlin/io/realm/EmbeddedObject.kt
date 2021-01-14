package io.realm

import io.realm.base.BaseRealmModel

interface EmbeddedObject<E: EmbeddedObject<E>> : BaseRealmModel {
}