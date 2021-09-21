package io.realm.test

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * All classes (Realm, RealmObject, RealmResults, RealmList) that expose notifications should
 * implement this interface to be sure that we test common behaviour across those classes.
 */
interface NotificationTests {

    // Verify that the initial element in a Flow is the element itself
    // TODO Is this the semantics we want?
    @Test
    fun initialElement()

    // Verify that a notification is triggered on updates
    @Test
    fun observe()

    // Verify that a flow can be cancelled
    @Test
    fun cancelObserve()

    // Verify that `null` is emitted and the Flow is closed whenever the object
    // being observed is deleted.
    @Test
    fun deleteObservable()

    // Verify that closing the Realm while inside a flow throws an exception (I think)
    @Test
    @Ignore // Wait for https://github.com/realm/realm-kotlin/pull/300 to be merged before fleshing this out
    fun closeRealmInsideFlowThrows()

    // Currently, closing a Realm will not cancel any flows from Realm
    //
    @Test
    @Ignore // Until proper Realm tracking is in place
    fun closingRealmDoesNotCancelFlows()

    @Test
    fun addChangeListener() {
        // FIXME Implement in another PR
    }

    @Test
    fun addChangeListener_emitOnProvidedDispatcher() {
        // FIXME Implement in another PR
    }

    @Test
    fun openSameRealmFileWithDifferentDispatchers() {
        // FIXME
    }

    // Verify that the Main dispatcher can be used for both writes and notifications
    // It should be considered an anti-pattern in production, but is plausible in tests.
    @Test
    fun useMainDispatchers() {
        // FIXME
    }

    // Verify that users can use the Main dispatcher for notifications and a background
    // dispatcher for writes. This is the closest match to how this currently works
    // in Realm Java.
    @Test
    fun useMainNotifierDispatcherAndBackgroundWriterDispatcher() {
        // FIXME
    }

    // Verify that the special test dispatchers provided by Google also when using Realm.
    @Test
    fun useTestDispatchers() {
        // FIXME
    }
}
