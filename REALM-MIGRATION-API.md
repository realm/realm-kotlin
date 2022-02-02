# Automatic Migration API Design

We agreed on keeping a simple solution with the minimum building blocks. The migration block signature would be:

```
(oldSchemaVersion: Int, before: DynamicRealm, after: Realm) -> Unit
```

How do we expose migrations to the users, lambdas or interfaces?

## 1. Type alias
- ✅ Easy discoverability
- ✅ Light weight
- ❌ No documentation support

```
typealias AutomaticMigrationBlock = (oldSchemaVersion: Int, before: DynamicRealm, after: Realm) -> Unit

typealias ManualMigrationBlock = (oldSchemaVersion: Int, realm: DynamicRealm) -> Unit

Configuration {
    fun migration(migration: AutomaticMigrationBlock)

    fun migration(migration: ManualMigrationBlock)
}
```

### Separate file usage example

```
// this code sits in migration.kt

val myMigration: AutomaticMigrationBlock = { oldSchemaVersion: Int, before: DynamicRealm, after: Realm ->
    (...migration block...)
}
```

```
val config = RealmConfiguration.Builder()
                .migration(myMigration)
```

### Embedded usage example 

```
val config = RealmConfiguration.Builder()
                .migration{ oldSchemaVersion, before, after ->
                    (...migration block...)
                }
```

## 2. Interfaces
- ✅ Easier discoverability
- ✅ Support documentation
- ❌ Verbose
- ❌ Heavier than a type alias

```
interface AutomaticMigration {
    fun migrate(oldSchemaVersion: Int, before: DynamicRealm, after: Realm)
}

interface ManualMigration {
    fun migrate(oldSchemaVersion: Int, realm: DynamicRealm)
}

interface Configuration {
    (...)

    fun migration(migration: AutomaticMigration)
    
    fun migration(migration: ManualMigration)

    (...)
}
```

### Separate file usage example

```
// this code sits in migration.kt

object MyMigration: AutomaticMigration {
    override fun migration(oldSchemaVersion: Int, before: DynamicRealm, after: Realm) {
        (...migration block...)
    }
}
```

```
val config = RealmConfiguration.Builder()
                .migration(MyMigration)
```

### Embedded usage example 

```
val config = RealmConfiguration.Builder()
                .migration(object MyMigration: AutomaticMigration {
                    override fun migration(oldSchemaVersion: Int, before: DynamicRealm, after: Realm) {
                        (...migration block...)
                    }
                })
```

## 3. Functional interfaces 🥇
- ✅ Type alias discover signature
- ✅ Embedding it in the configuration is easier
- ✅ Documentation support
- ❌ Heavier than a type alias

Such as type alias, but allows better documentation, if we like to expose them to the users this should be the way to go.

```
fun interface AutomaticMigration {
    fun migrate(oldSchemaVersion: Int, before: Realm, after: Realm)
}

fun interface ManualMigrationBlock {
    fun migrate(oldSchemaVersion: Int, realm: DynamicRealm)
} 

Configuration {
    fun migration(migration: AutomaticMigrationBlock)

    fun migration(migration: ManualMigrationBlock)
}
```

### Separate file usage example

```
// this code sits in migration.kt

val myMigration = AutomaticMigration { oldSchemaVersion, before, after ->
    (...migration block...)
}
```

```
val config = RealmConfiguration.Builder()
                .migration(myMigration)
```

### Embedded usage example 

```
val config = RealmConfiguration.Builder()
                .migration{ oldSchemaVersion, before, after ->
                    (...migration block...)
                }
```
