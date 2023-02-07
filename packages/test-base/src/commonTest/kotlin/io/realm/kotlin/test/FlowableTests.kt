package io.realm.kotlin.test

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * All tests classes that tests classes exposing notifications (Realm, RealmObject, RealmResults,
 * RealmList) should implement this interface to be sure that we test common behaviour across those
 * classes.
 */
interface FlowableTests {

    // Verify that the initial element in a Flow is the element itself
    // TODO Is this the semantics we want?
    @Test
    fun initialElement()

    // Verify that a notification is triggered on updates
    @Test
    fun asFlow()

    // Verify that a flow can be cancelled
    @Test
    fun cancelAsFlow()

    // Verify that closing the Realm while inside a flow throws an exception (I think)
    @Test
    @Ignore // Wait for https://github.com/realm/realm-kotlin/pull/300 to be merged before fleshing this out
    fun closeRealmInsideFlowThrows()

    // Currently, closing a Realm will not cancel any flows from Realm
    //
    @Test
    fun closingRealmDoesNotCancelFlows()

    // @Test
    // fun addChangeListener_emitOnProvidedDispatcher() {
    //     // FIXME Implement in another PR
    // }

    // @Test
    // fun addChangeListener() {
    //     // FIXME Implement in another PR
    // }

    // @Test
    // fun openSameRealmFileWithDifferentDispatchers() {
    //     // FIXME
    // }

    // Verify that the Main dispatcher can be used for both writes and notifications
    // It should be considered an anti-pattern in production, but is plausible in tests.
    // @Test
    // fun useMainDispatchers() {
    //     // FIXME
    // }

    // Verify that users can use the Main dispatcher for notifications and a background
    // dispatcher for writes. This is the closest match to how this currently works
    // in Realm Java.
    // @Test
    // fun useMainNotifierDispatcherAndBackgroundWriterDispatcher() {
    //     // FIXME
    // }

    // Verify that the special test dispatchers provided by Google also when using Realm.
    // @Test
    // fun useTestDispatchers() {
    //     // FIXME
    // }
}
