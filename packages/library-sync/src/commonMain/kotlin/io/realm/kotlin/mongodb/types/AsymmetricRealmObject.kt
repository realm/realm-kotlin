package io.realm.kotlin.mongodb.types

import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmObject

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
 * It is possible to configure single [io.realm.kotlin.mongodb.sync.SyncConfiguration] schema with
 * a mix of asymmetric, embedded, and standard realm objects.
 */
public interface AsymmetricRealmObject : BaseRealmObject
