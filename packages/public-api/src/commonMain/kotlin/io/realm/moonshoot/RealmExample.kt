//package io.realm.moonshoot
//
//import io.realm.RealmConfiguration
//import io.realm.RealmObject
//import io.realm.base.BaseRealmModel
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.flow.*
//import kotlinx.coroutines.launch
//import kotlinx.datetime.Clock
//import kotlinx.datetime.Instant
//import kotlin.coroutines.CoroutineContext
//import kotlin.reflect.KClass
//
///**
// * This file shows how an API could look like if we instead of the current thread-confied API made the entire API
// * based on streams of immutable data except inside write transactions.
// *
// * The idea is that this would be a perfect fit for a Coroutine heavy world as well as remove a lot of the complaints
// * we have gotten for the Realm Java API: 1) You can keep one Realm open across the entire app. No need to open/close
// * on each thread. 2) Everything is frozen, so no Illegal Thread Access exceptions
// *
// * See https://docs.google.com/document/d/1usXLWVyAqAcDrH0UcYJreB6mYbHho5IWhQTKOcUacWQ/edit#heading=h.o8o3cnwjpb7s
// * for more information.
// */
//
//val defaultContext = object: CoroutineContext {
//    override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R { TODO() }
//    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? { TODO() }
//    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext { TODO() }
//}
//val viewModelScope: CoroutineScope = CoroutineScope(defaultContext)
//val defaultRealmConfiguration = RealmConfiguration.Builder().build()
//interface RealmResults<E: Any> : List<E> {
//    fun <E: Any> asProjection(kClass: KClass<E>): RealmResults<E>
//    fun observe(): Flow<RealmResults<E>>
//}
//
//interface Queryable<E: RealmObject> {
//    suspend fun filter(filter: String): RealmQuery<E> { TODO() }
//    suspend fun sort(field: String): RealmQuery<E> { TODO() }
//    suspend fun distinct(field: String): RealmQuery<E> { TODO() }
//    fun observe(): Flow<RealmResults<E>> { TODO() }
//}
//
//class RealmQuery<E: RealmObject> {
//    suspend fun findAll(): RealmResults<E> { TODO() }
//    suspend fun findFirst(): E? { TODO() }
//    suspend fun filter(filter: String): RealmQuery<E> { TODO() }
//    suspend fun sort(field: String): RealmQuery<E> { TODO() }
//    suspend fun distinct(field: String): RealmQuery<E> { TODO() }
//    fun observe(): Flow<RealmResults<E>> { TODO() }
//    fun observeFirst(): Flow<E?> { TODO() }
////    fun addChangeListener
//}
//
//class RealmList<E: RealmObject>(override val size: Int = 0): List<E>, Queryable<E> {
//    override fun contains(element: E): Boolean { TODO("Not yet implemented") }
//    override fun containsAll(elements: Collection<E>): Boolean { TODO("Not yet implemented") }
//    override fun get(index: Int): E { TODO("Not yet implemented") }
//    override fun indexOf(element: E): Int { TODO("Not yet implemented") }
//    override fun isEmpty(): Boolean { TODO("Not yet implemented") }
//    override fun iterator(): Iterator<E> { TODO("Not yet implemented") }
//    override fun lastIndexOf(element: E): Int { TODO("Not yet implemented") }
//    override fun listIterator(): ListIterator<E> { TODO("Not yet implemented") }
//    override fun listIterator(index: Int): ListIterator<E> { TODO("Not yet implemented") }
//    override fun subList(fromIndex: Int, toIndex: Int): List<E> { TODO("Not yet implemented") }
//}
//
//interface LiveRealm {
//
//
//
//}
//
//
//interface Realm {
//    companion object {
//        fun open(config: RealmConfiguration = defaultRealmConfiguration): Realm {
//            TODO()
//        }
//
//    }
//
//    fun <E : RealmObject> objects(modelClass: KClass<E>): RealmQuery<E>, Queryable<E> { TODO() }
//
//
//    suspend fun executeTransaction(function: (realm: Realm) -> Unit) {
//    }
//
//
//
////
////    fun add(project: Project) {
////        TODO("Not yet implemented")
////    }
//}
//
//annotation class Projection(val clazz: KClass<out RealmObject>)
//// --------
//
//
//class Project(var name: String, var created: Instant, val tasks: RealmList<Task> = RealmList()): RealmObject {}
//class Task: RealmObject {}
//
//class AppModel {
//
//    // Open Realm. This is a global object readable from all Threads. So can be considered a Singleton.
//    // This can massively reduce our caching layer
//    private val realm: Realm = Realm.open()
//
//    suspend fun getProjects(): RealmResults<Project> {
//        val results: RealmResults<Project> = realm.objects(Project::class)
//                .filter("name = 'test'")
//                .sort("project")
//                .observe()
//
//
//        val otherResults = results.sort("name").findAll()
//        val project = otherResults.first()
//        project.tasks.sort("name")
//
//
//
//        results.getQuery().sort("name").findAll()
//    }
//
////    suspend fun createProject(project: Project) {
////        realm.executeTransaction {
////
////            it.objects(Projects::class).first()?.
////
////
////            val project = it.add(Project("Project", Clock.System.now()))
////            it.find(Project::class, "id")?.apply {
////
////            }
////
////            project.thaw {
////
////            }
////
////
////
////
////        }
////        realm.objects(Project::class).first() // Might n
////    }
//}
//
//@Projection(Project::class)
//data class ProjectViewData(val name: String, val noOfTasks: Int)
////
////@Projection(Project::class)
////data class CreateProject(val name: String, val created: Instant)
//
//class ViewModel {
//    private val model = AppModel()
//
//    suspend fun getProjects(): Flow<List<ProjectViewData>> {
//        return model.getProjects().map {
//            it.map { project ->
//                ProjectViewData(project.name, project.tasks.size)
//            }
//        }
//    }
//
//    fun createNewProject(name: String) {
//        viewModelScope.launch(Dispatchers.Default) {
//            model.getProjects().observe()
//                    .filter { it.size == 1 }
//                    .collect() {
//                        updateUI(it.first())
//                    }
//        }
//    }
//
//    fun markAsDone()
//
//}
//
//class View {
//    val viewModel = ViewModel()
//
//
//}
//
//
//
//
//
//// All notifications are Flows which provide structured concurrency, so they
//// will automatically be canceled when the scope is.
//GlobalScope.launch {
//    // The `realm` instance can be used from any Context (thread)
//    // Results are lazy evaluated, so we are only building the query now
//    val results: RealmResults<Project> = realm.objects(Project::class)
//
//    // If accessed, it is run against the current frozen version of Realm
//    val project: Project? = results.first()
//
//    // If Observed a NotifictionListener is registered against the Live Realm and updates will be received from there.
//    results.observe().collect {
//
//    }
//}
//
//
//
//
//
