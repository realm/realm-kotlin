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

package io.realm.kotlin.types

/**
 * Asymmetric Realm objects are "write-only" objects that are only supported in synchronized realms
 * configured for [Flexible Sync](https://www.mongodb.com/docs/realm/sdk/kotlin/sync/#flexible-sync).
 *
 * They are useful in write-heavy scenarios like sending sending telemetry data. Once the data is
 * sent to the server, it is also automatically deleted on the device.
 *
 * The benefit of using [AsymmetricRealmObject] is that the performance of each sync operation
 * is much higher. The drawback is that an [AsymmetricRealmObject] is synced unidirectional, so it
 * cannot be queried or manipulated once inserted.
 *
 * Asymmetric objects also has limits on the schema they can support. Asymmetric objects can
 * only link to [EmbeddedRealmObject]s, not [RealmObject]s or other asymmetric objects. Neither
 * [RealmObject]s nor [EmbeddedRealmObject]s can link to [AsymmetricRealmObject]s.
 *
 * It IS possible to combine asymmetric, embedded and standard realm objects in a single
 * [io.realm.kotlin.mongodb.sync.SyncConfiguration] schema.
 */
public interface AsymmetricRealmObject : BaseRealmObject
