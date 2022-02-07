package io.realm.notifications

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
 * realm.filter<Person>().asFlow()
 *   .collect { it: ListChange<RealmResults<Person>> ->
 *       when(result) {
 *          is InitialList -> setUIList(it.list)
 *          is UpdatedList -> updateUIList(it) // Android RecyclerView knows how to animate ranges
 *          is DeletedList -> deleteUIList(it.list)
 *       }
 *   }
 *
 * // Variant 2: Switch on the state property
 * realm.filter<Person>().asFlow()
 *   .collect { it: ListChange<RealmResults<Person>> ->
 *       when(it.state) {
 *          INITIAL -> setUIList(it.list)
 *          UPDATED -> updateUIList(it.list) // Use DiffUtil to calculate and animate changes
 *          DELETED -> deleteUIList(it.list)
 *       }
 *   }
 *
 * // Variant 3: Just pass on the list
 * realm.filter<Person>().asFlow()
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
 * realm.filter<Person>().asFlow()
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
 * realm.filter<Person>().asFlow()
 *   .collect { it: ListChange<RealmResults<Person>> ->
 *       when(it.state) {
 *          INITIAL -> setList(it.list)
 *          UPDATED -> {
 *              val update = it as UpdatedList
 *              updateList(
 *                  update.list,
 *                  update.deletionRanges,
 *                  update.insertionRanges,
 *                  update.changeRanges
 *             )
 *          }
 *          DELETED -> removeList()
 *       }
 *   }
 * ```
 */
sealed interface ListChange<out T : List<*>> {
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

    val list: T?
}
interface InitialList<T : List<*>> : ListChange<T>
interface UpdatedList<T : List<*>> : ListChange<T> {
    val deletions: IntArray
    val insertions: IntArray
    val changes: IntArray
    val deletionRanges: Array<ListChange.Range>
    val insertionRanges: Array<ListChange.Range>
    val changeRanges: Array<ListChange.Range>
}
interface DeletedList<T : List<*>> : ListChange<T>
