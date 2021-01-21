package io.realm.example

import io.realm.*
import io.realm.annotations.PrimaryKey
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.reflect.KMutableProperty1

class Project(@PrimaryKey var id: Long, var name: String = "", var created: Instant = Clock.System.now(), val tasks: RealmList<Task> = RealmList()): RealmObject<Project>
class Task(var name: String): EmbeddedRealmObject<Task>

class ApiExample {

    val realm = Realm.open()

    fun doSomethingWith(vararg arg: Any?) { TODO() }

    fun crud() {

        // Create object
        GlobalScope.launch {
            realm.executeTransaction {
                val project = add(Project(id = 1, name = "Example Project")).apply {
                    tasks.add(Task("Item 1"))
                    tasks.add(Task("Item 2"))
                }

                // Adding embedded objects using property reference
                add(Task("Item 3"), project, Project::tasks)

                // Add embedded object using string reference
                add(Task("Item "), project, "tasks")

            }
        }

        // Read object
        GlobalScope.launch {
            val project: Project? = realm.find(Project::class, 1)
            project?.name
        }

        // Update object
        GlobalScope.launch {
            val project: Project = realm.find(Project::class, 1)!!
            var prop: KMutableProperty1<Project, String> = Project::name
            val projectId = project.id
            realm.executeTransaction(project) { project: Project? ->
                project?.name = "New name"
            }

            // Lookup object inside transaction
            realm.executeTransaction {
                find(project)?.name = "Single field update"
                find(project)?.apply {
                    name = "Batch updates"
                    created = Clock.System.now()
                }
            }

            // Use object update method
            realm.executeTransaction {
                // QUESTION: While an update method looks appealing at a glance, the null check
                //  might just make it annoying? Should we make it so the update method only
                //  triggers if the object is still around and use implicit receiver?
                //  This might introduce subtle bugs if unaware that object was deleted?
                project.update {
                    it?.apply {
                        name = "New name"
                    }
                }
            }


            realm.executeTransaction {
                project.name = "New name" // Throw exception as frozen object cannot be modified
            }
        }

        // Delete object
        GlobalScope.launch {
            realm.executeTransaction {
                deleteAll() // Delete all objects
            }

            realm.executeTransaction {
                val project = find(Project::class, 1)
            }
        }
    }

    fun observingChanges() {
        val query: RealmQuery<Project> = realm.where(Project::class)

        // Using Flows
        GlobalScope.launch {
            query.observe().collect { it: RealmResults<Project> ->
                doSomethingWith(it)
            }

            query.observeChangesets().collect {
                doSomethingWith(it.collection, it.getInsertions())
            }
        }

        // Using changelisteners
        query.addChangeListener {
            doSomethingWith(it.collection, it.getInsertions())
        }

        // Observing aggregates
        GlobalScope.launch {
            // Continously getting updates
            query.min("created").collect { doSomethingWith(it!!) }

            // Get one results
            val minValue: Number? = query.min("created").first()
        }
    }

    fun queries() {
        // Queries that drive the UI
        GlobalScope.launch {
            realm.where(Project::class, "created < ${Clock.System.now()}")
                    .filter("tasks.@count < 5")
                    .sort("name")
                    .observe()
                    .collect { it: RealmResults<Project> ->
                        doSomethingWith(it)
                    }

            realm.find(Project::class, 1)!!.tasks
                    .where("name BEGINSWITH 'Task'")
                    .sort("name")
                    .observeFirst()
                    .collect { task: Task? ->
                        doSomethingWith(task)
                    }
        }

        // Business logic queries (inside transaction)
        GlobalScope.launch {
            realm.executeTransaction {
                val project1 = find(Project::class, 1)!!
                val project2 = find(Project::class, 2)!!
                val task1: Task? = project1.tasks.where("name = 'My Task'").findFirst()
                val task2: Task? = project2.tasks.where("name = 'My Task'").findFirst()

                if (project1.created < project2.created) {
                    doSomethingWith(task1, task2)
                }
            }
        }
    }

    // Realworld example of writes:
    // https://github.com/WildAid/o-fish-android/blob/main/app/src/main/java/org/wildaid/ofish/data/RealmDataSource.kt#L112


    suspend fun badThings1_advanceRealm() {

        // Realm can advance between queries
        val project = realm.find(Project::class, 1)!!
        val tasks: RealmResults<Task> = realm.where(Task::class, "project.id = 1").findAll()

        // Solutions

        // 1. Navigate the object graph instead of multiple queries
        project.tasks

        // 2. Express what you want in a single query
        // Not possible in this example

        // 3. Use realm.pin
        realm.pin {
            val project = realm.find(Project::class, 1)!!
            val tasks: RealmResults<Task> = realm.where(Task::class, "project.id = 1").findAll()
            project.tasks == tasks // true
        }

        // 4. Run logic inside write transaction
        realm.executeTransaction {
            val project = realm.find(Project::class, 1)!!
            val tasks: RealmResults<Task> = realm.where(Task::class, "project.id = 1").findAll()
            project.tasks == tasks // true
        }

        realm.write {
            val project = realm.where(Project::class,"id == 1").findFirstSync()!!
            val tasks: RealmResults<Task> = realm.where(Task::class, "project.id = 1").findAllSync()
            project.tasks == tasks // true
        }

    }

    suspend fun badThings2_assumeThatFrozenObjectIsUpToDate() {
        val project = realm.find(Project::class, 1)!!
        realm.executeTransaction {
            if (project.created < Clock.System.now()) {
                // Run some logic inside the transaction based on old state of project created from
                // outside the transaction
            }
        }

        // Solutions

        // Documentation: By lifting thread constraints, this kind of pattern becomes possible, where
        // it previously would crash when using async transactions. Synchronous transactions would
        // automatically update all objects so it would be safe.

        // Correct way is fetching all objects inside the transaction
        realm.executeTransaction {
            val project = realm.find(Project::class, 1)!!
            if (project.created < Clock.System.now()) {
                // Run correct logic
            }
        }
    }

    suspend fun badThings3_tryingToWriteToFrozenObject() {
        val project = realm.find(Project::class, 1)!!
        realm.executeTransaction {
            project.name = "New name" // Throws exception
        }

        // Solutions
        // This is no different than today. Good exception message and documentation.

        // 1. Use find inside transaction
        realm.executeTransaction {
            find(project)!!.name = "New name"
        }

        // 2. Use update method inside transaction
        realm.executeTransaction {
            project.update {
                it?.apply {
                    name = "New Name"
                }
            }

            // 3. Pass in argument
            realm.executeTransaction(project) { p ->
                p?.apply {
                    name = "New Name"
                }
            }
        }
    }
}
