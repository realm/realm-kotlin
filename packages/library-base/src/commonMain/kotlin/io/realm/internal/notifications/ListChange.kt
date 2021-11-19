package io.realm.notifications

import io.realm.RealmObject


/**
 * This sealed interface describe the possible changes that happen happen to a list collection,
 * currently [io.realm.RealmList] or [io.realm.RealmResults].
 *
 * The specific states are both represented through the [ListChange.state] property but also as
 * specific subclasses.
 *
 * Changes can thus be consumed in a number of ways:
 *
 * ```
 * // Variant 1: Switch on the sealed interface
 * realm.object(Person::class).observe()
 *   .collect { it: ListChange<RealmResults<Person>> ->
 *       when(result) {
 *          is InitialList -> setList(it.list)
 *          is UpdatedList -> updateList(it.list)
 *          is DeletedList -> deleteList(it.list)
 *       }
 *   }
 *
 * // Variant 2: Switch on the state property
 * realm.object(Person::class).observe()
 *   .collect { it: ListChange<RealmResults<Person>> ->
 *       when(it.state) {
 *          INITIAL -> setList(it.list)
 *          UPDATED -> updateList(it.list)
 *          DELETED -> deleteList(it.list)
 *       }
 *   }
 *
 * // Variant 3: Just pass on the list
 * realm.object(Person::class).observe()
 *   .collect { it: ListChange<RealmResults<Person>> ->
 *       handleChange(it.list)
 *   }
 * ```
 *
 * For state changes of [ListChange.State.UPDATED], extra information is provided describing
 * the changes from the previous version. This information is formatted in a way that can be
 * feed directly to drive animations on UI components like `RecyclerView`. In order to access this
 * information, the [ListChange] must be cast to the appropriate subclass
 *
 * ```
 * // Variant 1: Automatic cast using sealed interface
 * realm.object(Person::class).observe()
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
 * // Variant 1: Manual switch on state property
 * realm.object(Person::class).observe()
 *   .collect { it: ListChange<RealmResults<Person>> ->
 *       when(it.state) {
 *          UPDATED -> {
 *              val update = it as UpdatedList
 *              updateList(
 *                  update.list,
 *                  update.deletionRanges,
 *                  update.insertionRanges,
 *                  update.changeRanges
 *             )
 *          }
 *       }
 *   }
 * ```
 */
sealed interface ListChange<T: List<RealmObject>> {

    enum class State {
        /**
         * This state is used the first time a change listener or flow is triggered. It will emit the
         * initial state of the [io.realm.RealmList] or [io.realm.RealmResults].
         */
        INITIAL,

        /**
         * This state is used for every subsequent update after the first, as long as the object
         * being observed is available.
         */
        UPDATED,

        /**
         * This state is used if the parent object owning collection is deleted.
         * If this happens, [ListChange.list] returns an en empty collection.
         */
        DELETED,
    }

    data class Range(
        /**
         * The start index of this change range.
         */
        val startIndex: Int,
        /**
         * How many elements are inside this range.
         */
        val length: Int
    )

    val state: State
    val list: T
}
interface InitialList<T: List<RealmObject>>: ListChange<T>
interface UpdatedList<T: List<RealmObject>>: ListChange<T> {
    val deletions: IntArray
    val insertions: IntArray
    val changes: IntArray
    val deletionRanges: Array<ListChange.Range>
    val insertionRanges: Array<ListChange.Range>
    val changeRanges: Array<ListChange.Range>
}
interface DeletedList<T: List<RealmObject>>: ListChange<T>


//interface ListChange<T: List<*>?> {
//
//    /**
//     * Returns the newest state of the list collection being observed.
//     *
//     * @return the newest state of the list.
//     */
//    val list: T
//
//    /**
//     * Returns the state represented by this change. See [ChangeState] for a description of the
//     * different states a changeset can be in.
//     *
//     * @return what kind of state is represented by this changeset.
//     * @see ChangeState
//     */
//    val state: ChangeState
//
//    /**
//     * The deleted indices in the previous version of the collection.
//     *
//     * @return the indices array. A zero-sized array will be returned if no objects were deleted.
//     */
//    val deletions: IntArray
//
//    /**
//     * The inserted indices in the new version of the collection.
//     *
//     * @return the indices array. A zero-sized array will be returned if no objects were inserted.
//     */
//    val insertions: IntArray
//
//    /**
//     * The modified indices in the new version of the collection.
//     *
//     *
//     * For [io.realm.RealmResults], this means that one or more of the properties of the object at
//     * the given index were modified (or an object linked to by that object was modified).
//     *
//     * @return the indices array. A zero-sized array will be returned if objects were modified.
//     */
//    val changes: IntArray
//
//    /**
//     * The deleted ranges of objects in the previous version of the collection.
//     *
//     * @return the [Range] array. A zero-sized array will be returned if no objects were deleted.
//     */
//    val deletionRanges: Array<Range>
//
//    /**
//     * The inserted ranges of objects in the new version of the collection.
//     *
//     * @return the [Range] array. A zero-sized array will be returned if no objects were inserted.
//     */
//    val insertionRanges: Array<Range>
//
//    /**
//     * The modified ranges of objects in the new version of the collection.
//     *
//     * @return the [Range] array. A zero-sized array will be returned if no objects were modified.
//     */
//    val changeRanges: Array<Range>
//
//    /**
//     * This class represents changes next to each other through a start index and a length.
//     * This is mostly useful for integration with other UI frameworks.
//     */
//    data class Range(
//        /**
//         * The start index of this change range.
//         */
//        val startIndex: Int,
//        /**
//         * How many elements are inside this range.
//         */
//        val length: Int
//    )
//}