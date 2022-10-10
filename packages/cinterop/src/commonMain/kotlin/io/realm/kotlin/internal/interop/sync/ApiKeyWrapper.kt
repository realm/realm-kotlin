package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.ObjectIdWrapper

public data class ApiKeyWrapper public constructor(public val id: ObjectIdWrapper, public val value: String?, public val name: String, public val disabled: Boolean)
