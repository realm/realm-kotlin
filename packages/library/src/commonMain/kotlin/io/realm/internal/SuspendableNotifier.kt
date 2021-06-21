package io.realm.internal

import io.realm.Callback
import io.realm.Cancellable
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.VersionId
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.isValid
import kotlinx.atomicfu.AtomicRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Class responsible for controlling notifications for a Realm. It does this by wrapping a live Realm on which
 * notifications can be registered. Since all objects that is otherwise exposed to users are frozen, they need
 * to be thawed when reaching the live Realm.
 *
 * For Lists and Objects, this can result in the object no longer existing. In this case, Flows complete
 * immediately and changelisteners will never be triggered (Is this the semantics we want? What do Room do?).
 *
 * Notifications are not allowed on live objects. But it is assumed that other layers check that invariant before
 * methods on this class is called.
 */
internal class SuspendableNotifier(private val owner: Realm, private val dispatcher: CoroutineDispatcher) {

    companion object {
        val NO_OP_NOTIFICATION_TOKEN = object: Cancellable {
            override fun cancel() { /* Do Nothing */ }
        }
    }

    // Must only be accessed from the dispatchers thread
    private val realm: NotifierRealm by lazy {
        NotifierRealm(owner.configuration, dispatcher)
    }

    fun checkIsSendingNotification(errorMessage: String) {
        // FIXME
    }

    /**
     * Listen to changes to a Realm.
     *
     * This flow is guaranteed to emit before any other streams listening to individual objects or
     * query results.
     */
    fun realmChanged(): Flow<Pair<NativePointer, VersionId>> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                token.value = addRealmChangedListener { frozenRealm ->
                    trySend(frozenRealm)
                }
            }
            awaitClose {
                token.value.cancel()
            }
        }
    }

    fun addRealmChangedListener(callback: Callback<Pair<NativePointer, VersionId>>): Cancellable {
        // FIXME: Waiting for RealmInterop to have support for global Realm changed
        // Access `realm` to create the Realm instance on the dispatcher thread
        println("TODO: Register a global change listener for: ${realm.configuration.path}")
        return object : Cancellable {
            override fun cancel() {
            }
        }
    }

    /**
     * Listen to changes to a RealmResults
     */
    fun <T: RealmObject> resultsChanged(results: RealmResults<T>): Flow<RealmResults<T>> {
        println("resultsChanged: Create callbackFlow")
        return callbackFlow {
            println("CallbackFlow created")
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                println("Add ChangeListener")
                val newToken = addResultsChangedListener(results) { frozenResults ->
                    // Realm should already have been updated with the latest version
                    // So `owner` should as a minimum be at the same version as the notification Realm.
                    println("Offer result: ${frozenResults.size}")
                    trySend(frozenResults)
                }
                token.value = newToken
            }
            awaitClose {
                println("Cancel token: $token")
                token.value.cancel()
            }
        }
    }

    // FIXME Need to expose change details to the user
    //  https://github.com/realm/realm-kotlin/issues/115
    /**
     * Register a change listener on a live RealmResults. All objects returned in the callback are frozen.
     */
    fun <T: RealmObject> addResultsChangedListener(results: RealmResults<T>, callback: Callback<RealmResults<T>>) : Cancellable {
        val liveResults = results.thaw(realm.realmReference)
        println("thawed results")
        val token = RealmInterop.realm_results_add_notification_callback(
            liveResults.result,
            object : io.realm.interop.Callback {
                override fun onChange(collectionChanges: NativePointer) {
                    println("Collection changes recieved")
                    // FIXME: The Realm should have been frozen in `realmChanged` instead since we know all notifications
                    //  will come that version
                    val frozenRealm = RealmReference(owner, RealmInterop.realm_freeze(realm.realmReference.dbPointer))

                    // Notifications need to be delivered with the version they where created on, otherwise
                    // the fine-grained notification data might be out of sync.
                    val frozenResults = liveResults.freeze(frozenRealm)
                    callback.onChange(frozenResults)
                }
            }
        )
        println("Before returning token")
        return NotificationToken(callback, token)

    }

    /**
     * Listen to changes to a RealmObject through a [Flow]. If the object is deleted, the flow will complete.
     */
    fun <T: RealmObject> objectChanged(obj: T): Flow<T> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                token.value = addObjectChangedListener(obj) { frozenObj ->
                    trySend(frozenObj)
                }
            }
            awaitClose {
                token.value.cancel()
            }
        }
    }

    // FIXME Need to expose change details to the user
    //  https://github.com/realm/realm-kotlin/issues/115
    /**
     * Listen to changes to a RealmObject through a change listener. The callback will happen
     * on the configured [SuspendableNotifier.dispatcher] thread.
     */
    fun <T: RealmObject> addObjectChangedListener(obj: T, callback: Callback<T>): Cancellable {
        val liveObject: RealmObjectInternal = (obj as RealmObjectInternal).thaw(realm.realmReference.owner) as RealmObjectInternal
        if (!liveObject.isValid()) {
            return NO_OP_NOTIFICATION_TOKEN
        }
        val token = RealmInterop.realm_object_add_notification_callback(
            liveObject.`$realm$ObjectPointer`!!,
            object : io.realm.interop.Callback {
                override fun onChange(objectChanges: NativePointer) {
                    // TODO: What happens when the object is deleted?
                    // Realm should already have been updated with the latest version
                    // So `owner` should as a minimum be at the same version as the notification Realm.
                    callback.onChange(liveObject.freeze(owner.realmReference))
                }
            }
        )
        return NotificationToken(callback, token)
    }

    /**
     * Listen to changes to a RealmList through a [Flow]. If the list is deleted the flow will complete.
     */
    fun <T: RealmObject> listChanged(list: List<T>): Flow<List<T>> {
        TODO("Implement and convert method to use RealmList when available")
    }

    /**
     * Listen to changes to a RealmList through a change listener. The callback will happen
     * on the configured [SuspendableNotifier.dispatcher] thread.
     */
    fun <T: RealmObject> addListChangedListener(list: List<T>, callback: Callback<List<T>>): Cancellable {
        TODO("Implement and convert method to use RealmList when available")
    }

    fun close() {
        realm.close() // FIXME: Figure out when it is safe to close the Realm
    }

}
