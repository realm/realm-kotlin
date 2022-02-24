package io.realm.notifications

import io.realm.RealmList

/**
 * This sealed interface describe the possible changes that can happen to a list collection.
 *
 * The states are represented by the specific subclasses [InitialList], [UpdatedList] and
 * [DeletedList]. When the list is deleted an empty list is emitted instead of null.
 *
 * Changes can thus be consumed in a number of ways:
 *
 * ```
 * // Variant 1: Switch on the sealed interface
 * person.addresses.asFlow()
 *   .collect { it: ListChange<Address> ->
 *       when(result) {
 *          is InitialList -> setAddressesUIList(it.list)
 *          is UpdatedList -> updateAddressesUIList(it) // Android RecyclerView knows how to animate ranges
 *          is DeletedList -> deleteAddressesUIList(it.list)
 *       }
 *   }
 *
 *
 * // Variant 2: Just pass on the list
 * person.addresses.asFlow()
 *   .collect { it: ListChange<Address> ->
 *       handleChange(it.list)
 *   }
 * ```
 *
 * When the list is updated, extra information is provided describing the changes from the previous
 * version. This information is formatted in a way that can be feed directly to drive animations on UI
 * components like `RecyclerView`. In order to access this information, the [ListChange] must be cast
 * to the appropriate subclass.
 *
 * ```
 * person.addresses.asFlow()
 *   .collect { it: ListChange<Address> ->
 *       when(result) {
 *          is InitialList -> setList(it.list)
 *          is UpdatedList -> { // Automatic cast to UpdatedList
 *              updateList(
 *                  it.list,
 *                  it.deletionRanges,
 *                  it.insertionRanges,
 *                  it.changeRanges
 *             )
 *          }
 *          is DeletedList -> deleteList(it.list)
 *       }
 *   }
 * ```
 */
public sealed interface ListChange<T> {
    public val list: RealmList<T>
}

/**
 * Initial event to be observed on a [RealmList] flow. It contains a reference to the starting list
 * state. Note, this state might be different than the list the flow was registered on, if another
 * thread or device updated the object in the meantime.
 */
public interface InitialList<T> : ListChange<T>

/**
 * [RealmList] flow event that describes that an update has been performed on to the observed list. It
 * provides a reference to the list and a set of properties that describes the changes performed on
 * the list.
 */
public interface UpdatedList<T> : ListChange<T>, CollectionChangeSet

/**
 * This interface describes the event is emitted deleted on a [RealmList] flow. The flow will terminate
 * after observing this event.
 */
public interface DeletedList<T> : ListChange<T>
