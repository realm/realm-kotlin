package io.realm.internal.worker

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.interop.NativePointer

/**
 * Class responsible for controlling notifications for a Realm.
 * See https://docs.google.com/document/d/1bGfjbKLD6DSBpTiVwyorSBcMqkUQWedAmmS_VAhL8QU/edit to see a big picture
 * description.
 *
 * This class wraps a Live Realm and a task queue that is responsible for handling all notifications. The workflow
 * works roughly this way:
 *
 * 0) When created this class opens a Live Realm and creates a task queue.
 * 1) A frozen Realm/Object/Query adds an Intent to register a callback on this class' task queue. This consist of
 *    the pair (FrozenOwner, Callback).
 * 2) This class wraps the pair as a ThreadSafeReference: Pair(ThreadSafeReference(owner), Callback) and put it on
 *    the task queue.
 * 3) This class polls the task queue. When getting a message, it resolves the ThreadSafeReference and registers a
 *    notification with the callback, on the, now, live object.
 * 4) The callback is invoked using the normal notification machinery available in Realm Core. When invoked, this class
 *    will freeze the result and send it through the callback
 */

class NotifierThread(configuration: RealmConfiguration): JobThread(configuration) {
    val realm = Realm.open(configuration) // TODO: Figure out type hierachy to support a public Realm, a MutableRealm and a NotifierRealm
}