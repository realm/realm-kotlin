package io.realm.notifications

import io.realm.RealmList
import io.realm.RealmResults

/**
 * This sealed interface describe the possible changes that can happen to a list collection,
 * currently [io.realm.RealmList] or [io.realm.RealmResults].
 *
 * The states are represented by the specific subclasses [InitialList], [UpdatedList] and
 * [DeletedList]. When the list is deleted an empty list is emitted instead of null.
 *
 * Changes can thus be consumed in a number of ways:
 *
 * ```
 * // Variant 1: Switch on the sealed interface
 * realm.query<Person>().asFlow()
 *   .collect { it: ListChange<RealmResults<Person>> ->
 *       when(result) {
 *          is InitialList -> setUIList(it.list)
 *          is UpdatedList -> updateUIList(it) // Android RecyclerView knows how to animate ranges
 *          is DeletedList -> deleteUIList(it.list)
 *       }
 *   }
 *
 *
 * // Variant 2: Just pass on the list
 * realm.query<Person>().asFlow()
 *   .collect { it: ListChange<RealmResults<Person>> ->
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
 * realm.query<Person>().asFlow()
 *   .collect { it: ListChange<RealmResults<Person>> ->
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
public sealed interface ListChange<out T : List<*>> {
    public data class Range(
        /**
         * The start index of this change range.
         */
        val startIndex: Int,
        /**
         * How many elements are inside this range.
         */
        val length: Int
    )

    public val list: T
}

/**
 * Initial event to be observed on a [RealmList] or [RealmResults] flow. It contains a reference to the
 * starting list state. Note, this state might be different than the list the flow was registered on,
 * if another thread or device updated the object in the meantime.
 */
public interface InitialList<T : List<*>> : ListChange<T>

/**
 * [RealmList] or [RealmResults] flow event that describes that an update has been performed on to the
 * observed list. It provides a reference to the list and a set of properties that describes the changes
 * performed on the list.
 */
public interface UpdatedList<T : List<*>> : ListChange<T> {
    /**
     * The deleted indices in the previous version of the collection. It will be set as a zero-sized
     * array if no objects were deleted.
     */
    public val deletions: IntArray

    /**
     * The inserted indices in the new version of the collection. It will be set as a zero-sized
     * array if no objects were inserted.
     */
    public val insertions: IntArray

    /**
     * The modified indices in the new version of the collection.
     * <p>
     * For {@link RealmResults}, this means that one or more of the properties of the object at the given index were
     * modified (or an object linked to by that object was modified). It will be set as a zero-sized
     * array if no objects were changed.
     */
    public val changes: IntArray

    /**
     * The deleted ranges of objects in the previous version of the collection. It will be set as a zero-sized
     * array if no objects were deleted.
     */
    public val deletionRanges: Array<ListChange.Range>

    /**
     * The inserted ranges of objects in the new version of the collection. It will be set as a zero-sized
     * array if no objects were inserted.
     *
     * @return the {@link Range} array. A zero-sized array will be returned if no objects were inserted.
     */
    public val insertionRanges: Array<ListChange.Range>

    /**
     * The modified ranges of objects in the new version of the collection. It will be set as a zero-sized
     * array if no objects were changed.
     */
    public val changeRanges: Array<ListChange.Range>
}

/**
 * This interface describes the event is emitted deleted on a [RealmList] flow. The flow will terminate
 * after observing this event. This event would never be observed on a [RealmResults] as they cannot
 * be deleted.
 */
public interface DeletedList<T : List<*>> : ListChange<T>
