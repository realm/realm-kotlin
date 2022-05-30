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

package io.realm.mongodb.exceptions

/**
 * This exception is considered the top-level exception or general "catch-all" for problems related
 * to using Device Sync.
 *
 * This exception and subclasses of it will be passed to users through
 * [io.realm.mongodb.sync.SyncConfiguration.Builder.errorHandler] and the the exact reason must be
 * found in [Throwable.message].
 *
 * @see io.realm.mongodb.sync.SyncConfiguration.Builder.errorHandler which is responsible for
 * handling this type of exceptions.
 */
public open class SyncException : AppException {
    internal constructor(message: String) : super(message)
}

/**
 * Thrown when something has gone wrong with Device Sync in a way that is not recoverable.
 *
 * Generally, errors of this kind are due to incompatible versions of Realm and Atlas App Services
 * being used or bugs in the library or on the server, and the only fix would be installing a new
 * version of the app with a new version of Realm.
 *
 * It is still possible to use the Realm locally after this error occurs. However, this must be
 * done with caution as data written to the realm after this point risk getting lost as
 * many errors of this category will result in a Client Reset once the client
 * re-connects to the server.
 *
 * @see io.realm.mongodb.sync.SyncConfiguration.Builder.errorHandler which is responsible for
 * handling this type of exceptions.
 */
public class UnrecoverableSyncException : SyncException {
    internal constructor(message: String) : super(message)
}

/**
 * Thrown when the type of sync used by the server does not match the one used by the client, i.e.
 * the server and client disagrees whether to use Partition-based or Flexible Sync.
 */
public class WrongSyncTypeException : SyncException {
    internal constructor(message: String) : super(message)
}

/**
 * Thrown when the server does not support one or more of the queries defined in the
 * [io.realm.mongodb.sync.SubscriptionSet].
 */
public class BadFlexibleSyncQueryException : SyncException {
    internal constructor(message: String) : super(message)
}
