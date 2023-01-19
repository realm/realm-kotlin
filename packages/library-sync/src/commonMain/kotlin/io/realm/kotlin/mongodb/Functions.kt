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
package io.realm.kotlin.mongodb

import io.realm.kotlin.mongodb.ext.call

/**
 * A Functions manager to call remote Atlas Functions for the associated Atlas App Services Application.
 *
 * Functions are invoked using the extension function [Functions.call].
 *
 * @see [User.functions]
 */
public interface Functions {
    /**
     * The [App] that this function manager is associated with.
     */
    public val app: App

    /**
     * The [User] that this function manager is authenticated with.
     */
    public val user: User
}
