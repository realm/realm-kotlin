package io.realm

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * All classes (Realm, RealmObject, RealmResults, RealmList) that expose Callback notifications
 * should implement this interface to be sure that we test common behaviour across those classes.
 */
interface CallbackNotificationTests {

    // Verify that the latest, current element is emitted as the initial element
    @Test
    fun initialCallback()

    // Verify that the change listener is called on updates.
    @Test
    fun updateCallback()

    // Verify that the callback is notified with `null` if the object being observed is deleted.
    @Test
    fun observerDeletedCallback()

    // Verify that adding listeners to unmanaged objects throws.
    @Test
    fun addingListenerOnUnmanagedObjectThrows()

    @Test
    fun addingListenerOnClosedObjectThrows()

    // Verify what happens if you cancel the token on a different thread than it was created on.
    @Test
    fun cancelTokenInOtherThread() {
        // FIXME
    }

    // Verify that errors inside the Callback calculation propagate to users. Especially errors from Realm Core.
    @Test
    @Ignore
    fun errorInsideCallbackThrows() {
        // FIXME
    }

    // Verify that closing the Realm while inside a flow throws an exception (I think)
    @Test
    @Ignore
    fun closeRealmInsideCallbackThrows() {
        // FIXME
    }

    // Verify that users are required to manually clean up all listeners themselves.
    @Test
    @Ignore
    fun closingRealmDoesNotFreeListeners() {
        // FIXME Verify that listener native resources are not cleaned up when the Realm
        //  is closed (current behaviour).
    }
}
