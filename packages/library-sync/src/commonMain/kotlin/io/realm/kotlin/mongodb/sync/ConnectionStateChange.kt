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

package io.realm.kotlin.mongodb.sync

/**
 * A **connection state change** indicates a change in the [SyncSession]'s underlying connection
 * state.
 *
 * @property oldState the sync session's old connection state.
 * @property newState the sync session's new connection state.
 *
 * @see SyncSession.connectionState
 */
public data class ConnectionStateChange(val oldState: ConnectionState, val newState: ConnectionState)
