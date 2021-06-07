/*
 * Copyright 2021 Realm Inc.
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
package io.realm.sample.bookshelf

import io.realm.Cancellable
import io.realm.sample.bookshelf.database.RealmDatabase
import io.realm.sample.bookshelf.model.Book
import io.realm.sample.bookshelf.network.OpenLibraryApi
import kotlinx.coroutines.flow.Flow

class BookshelfSDK {
    private val api = OpenLibraryApi()
    private val database = RealmDatabase()

    @Throws(Exception::class)
    suspend fun getBookByTitle(title: String): List<Book> {
        return api.findBook(title).books
    }

    fun allBooks(): List<Book> {
        return database.getAllBooks()
    }

    fun allBooksAsFlowable(): Flow<List<Book>> {
        return database.getAllBooksAsFlowable()
    }

    fun allBooksAsCallback(success: (List<Book>) -> Unit): Cancellable {
        return database.getAllBooksAsCallback(success)
    }

    fun addToBookshelf(book: Book) {
        database.addBook(book)
    }

    fun removeFromBookshelf(title: String) {
        database.deleteBook(title)
    }

    fun clearBookshelf() {
        database.clearAllBooks()
    }

    // Platform specific logger is needed to debug notification
    fun onBookshelfChanged(block: () -> Unit): Cancellable {
        return database.onBookChange(block)
    }
}
