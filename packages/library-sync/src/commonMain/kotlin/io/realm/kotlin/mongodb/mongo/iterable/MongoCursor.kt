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

/**
 * The Mongo Cursor class is fundamentally an [Iterator] containing an additional
 * `tryNext()` method for convenience.
 *
 *
 * An application should ensure that a cursor is closed in all circumstances, e.g. using a
 * try-with-resources statement.
 *
 * @param <ResultT> The type of documents the cursor contains
</ResultT> */
class MongoCursor<ResultT> internal constructor(private val iterator: Iterator<ResultT>) :
	MutableIterator<ResultT>, java.io.Closeable {
	override fun hasNext(): Boolean {
		return iterator.hasNext()
	}

	override fun next(): ResultT {
		return iterator.next()
	}

	/**
	 * A special `next()` case that returns the next document if available or null.
	 *
	 * @return A `Task` containing the next document if available or null.
	 */
	fun tryNext(): ResultT? {
		return if (!iterator.hasNext()) {
			null
		} else iterator.next()
	}

	public override fun close() {}
}
