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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
