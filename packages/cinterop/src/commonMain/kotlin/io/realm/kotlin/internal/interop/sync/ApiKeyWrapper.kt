package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.ObjectIdWrapper
import io.realm.kotlin.internal.interop.ObjectIdWrapperImpl

public data class ApiKeyWrapper internal constructor(
    public val id: ObjectIdWrapper,
    public val value: String?,
    public val name: String,
    public val disabled: Boolean
) {

    // Used by JNI
    internal constructor(
        id: ByteArray,
        value: String?,
        name: String,
        disabled: Boolean
    ) : this(ObjectIdWrapperImpl(id), value, name, disabled)
}
