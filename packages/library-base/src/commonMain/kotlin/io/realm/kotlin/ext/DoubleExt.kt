/*
 * Copyright 2023 Realm Inc.
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
package io.realm.kotlin.ext

import io.realm.kotlin.types.Distance

/**
 * Create a [Distance] object from kilometers.
 */
public inline val Double.km: Distance
    get() = Distance.fromKilometers(this)

/**
 * Create a [Distance] object from miles.
 */
public inline val Double.miles: Distance
    get() = Distance.fromMiles(this)

/**
 * Create a [Distance] object from radians.
 */
public inline val Double.radians: Distance
    get() = Distance.fromRadians(this)
