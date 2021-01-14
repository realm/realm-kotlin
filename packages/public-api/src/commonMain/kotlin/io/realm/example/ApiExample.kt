package io.realm.example

import io.realm.*
import io.realm.annotations.PrimaryKey
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class Project(@PrimaryKey var id: Long, var name: String = "", var created: Instant = Clock.System.now(), val tasks: RealmList<Task> = RealmList()): RealmObject<Project>
class Task(var name: String): EmbeddedObject<Task>

class ApiExample {

    fun crud() {
        val realm = Realm.open()

        // Create object
        GlobalScope.launch {
            realm.executeTransaction { it: MutableRealm ->
                it.add(Project(id = 1, name = "Example Project")).apply {
                    tasks.add(Task("Item 1"))
                    tasks.add(Task("Item 2"))
                }
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

            // Pass in RealmObject as argument
            realm.executeTransaction(project) { realm, project: Project? ->
                project?.name = "New name"
            }

            // Lookup object inside transaction
            realm.executeTransaction {
                it.find(project)?.name = "New name"
            }

            realm.executeTransaction {
                project.name = "New name" // Throw exception as frozen object cannot be modified
            }
        }

        // Delete object
        GlobalScope.launch {
            realm.executeTransaction {
                it.deleteAll() // Delete all objects
            }

            realm.executeTransaction {
                it.find(Project::class, 1)?.deleteFromRealm()
            }
        }
    }

    fun queries() {
        // TODO
    }

    fun observingChanges() {
        // TODO
    }
}