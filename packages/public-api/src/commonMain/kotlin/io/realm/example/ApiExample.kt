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

    fun doSomethingWith(vararg arg: Any?) { TODO() }

    fun crud() {
        val realm = Realm.open()

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
        val realm = Realm.open()
        val query: RealmQuery<Project> = realm.filter(Project::class)

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
        val realm = Realm.open()

        // Queries that drive the UI
        GlobalScope.launch {
            realm.filter(Project::class, "created < ${Clock.System.now()}")
                    .filter("tasks.@count < 5")
                    .sort("name")
                    .observe()
                    .collect { it: RealmResults<Project> ->
                        doSomethingWith(it)
                    }

            realm.find(Project::class, 1)!!.tasks
                    .filter("name BEGINSWITH 'Task'")
                    .sort("name")
                    .observe()
                    .first()
        }

        // Business logic queries
        GlobalScope.launch {
            realm.executeTransaction {

                val project1 = find(Project::class, 1)!!
                val project2 = find(Project::class, 2)!!
                val task1 = project1.tasks.filter("name = 'My Task'").observe().first()
                val task2 = project2.tasks.filter("name = 'My Task'").observe().first()

                if (project1.created < project2.created) {
                    doSomethingWith(task1, task2)
                }
            }
        }
    }





    fun interactionMutableImmutableRealm() {

    }

}
