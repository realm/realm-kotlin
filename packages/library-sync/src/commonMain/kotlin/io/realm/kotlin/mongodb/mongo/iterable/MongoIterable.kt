/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.mongodb.mongo.iterable

import io.realm.internal.async.RealmResultTaskImpl
import io.realm.internal.jni.JniBsonProtocol
import io.realm.internal.network.NetworkRequest
import io.realm.internal.objectstore.OsMongoCollection
import io.realm.mongodb.RealmResultTask
import org.bson.codecs.configuration.CodecRegistry

// TODO

/**
 * The MongoIterable is the results from an operation, such as a `find()` or an
 * `aggregate()` query.
 *
 *
 * This class somewhat mimics the behavior of an [Iterable] but given its results are
 * obtained asynchronously, its values are wrapped inside a `Task`.
 *
 * @param <ResultT> The type to which this iterable will decode documents.
</ResultT> */
abstract class MongoIterable<ResultT> internal constructor(
	threadPoolExecutor: java.util.concurrent.ThreadPoolExecutor?,
	osMongoCollection: OsMongoCollection<*>,
	codecRegistry: CodecRegistry?,
	resultClass: java.lang.Class<ResultT>
) {
	protected val osMongoCollection: OsMongoCollection<*>
	protected val codecRegistry: CodecRegistry?
	private val resultClass: java.lang.Class<ResultT>
	private val threadPoolExecutor: java.util.concurrent.ThreadPoolExecutor?

	init {
		this.threadPoolExecutor = threadPoolExecutor
		this.osMongoCollection = osMongoCollection
		this.codecRegistry = codecRegistry
		this.resultClass = resultClass
	}

	abstract fun callNative(callback: NetworkRequest<*>?)

	/**
	 * Returns a cursor of the operation represented by this iterable.
	 *
	 *
	 * The result is wrapped in a `Task` since the iterator should be capable of
	 * asynchronously retrieve documents from the server.
	 *
	 * @return an asynchronous task with cursor of the operation represented by this iterable.
	 */
	operator fun iterator(): RealmResultTask<MongoCursor<ResultT>> {
		return RealmResultTaskImpl(threadPoolExecutor, object : Executor<MongoCursor<ResultT>?>() {
			@Nullable
			fun run(): MongoCursor<ResultT> {
				return MongoCursor(collection.iterator())
			}
		})
	}

	/**
	 * Helper to return the first item in the iterator or null.
	 *
	 *
	 * The result is wrapped in a `Task` since the iterator should be capable of
	 * asynchronously retrieve documents from the server.
	 *
	 * @return a task containing the first item or null.
	 */
	fun first(): RealmResultTask<ResultT> {
		val task: NetworkRequest<ResultT> = object : NetworkRequest<ResultT>() {
			protected fun mapSuccess(result: Any): ResultT? {
				val decodedCollection = mapCollection(result)
				val iter = decodedCollection.iterator()
				return if (iter.hasNext()) iter.next() else null
			}

			protected fun execute(callback: NetworkRequest<ResultT>?) {
				callNative(callback)
			}
		}
		return RealmResultTaskImpl(threadPoolExecutor, object : Executor<ResultT>() {
			@Nullable
			fun run(): ResultT {
				return task.resultOrThrow()
			}
		})
	}

	private val collection: Collection<ResultT>
		private get() = object : NetworkRequest<Collection<ResultT>?>() {
			protected fun mapSuccess(result: Any): Collection<ResultT> {
				return mapCollection(result)
			}

			protected fun execute(callback: NetworkRequest<Collection<ResultT>?>?) {
				callNative(callback)
			}
		}.resultOrThrow()

	private fun mapCollection(result: Any): Collection<ResultT> {
		val collection: Collection<*> =
			JniBsonProtocol.decode(result as String, MutableCollection::class.java, codecRegistry)
		val decodedCollection: MutableCollection<ResultT> = java.util.ArrayList<ResultT>()
		for (collectionElement in collection) {
			val encodedElement: String = JniBsonProtocol.encode(collectionElement, codecRegistry)
			decodedCollection.add(
				JniBsonProtocol.decode(
					encodedElement,
					resultClass,
					codecRegistry
				)
			)
		}
		return decodedCollection
	}
}
