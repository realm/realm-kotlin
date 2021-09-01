package io.realm.mongodb.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun getDispatcher(): CoroutineDispatcher = Dispatchers.IO
