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

package io.realm.kotlin.internal.interop

/**
 * A __synchronizable object__ that can be used to enforce mutual exclusion so that only one
 * thread can execute the accompanied block across all [synchronized]-call for the same
 * [SynchronizableObject] at a time.
 */
expect class SynchronizableObject()

/**
 * Execute the given `block` ensuring that no other [synchronized]-call is executing its block for
 * the same [SynchronizableObject] at the same time.
 *
 * This call should not be used recursively as the current implementation of [SynchronizableObject]
 * for Kotlin native is not backed by a reentrant lock.
 */
expect inline fun <R> synchronized(lock: SynchronizableObject, block: () -> R): R
