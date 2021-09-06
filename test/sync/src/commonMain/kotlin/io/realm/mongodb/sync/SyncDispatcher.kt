package io.realm.mongodb.sync

import kotlinx.coroutines.CoroutineDispatcher

expect fun getDispatcher(): CoroutineDispatcher
