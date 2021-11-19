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
