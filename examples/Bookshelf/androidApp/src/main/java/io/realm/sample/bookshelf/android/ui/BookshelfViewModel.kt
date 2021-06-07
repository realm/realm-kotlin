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
package io.realm.sample.bookshelf.android.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.realm.sample.bookshelf.BookshelfSDK
import io.realm.sample.bookshelf.model.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookshelfViewModel : ViewModel() {
    private var sdk = BookshelfSDK()

    val savedBooks: StateFlow<List<Book>> = sdk.allBooksAsFlowable()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var searchResults: SnapshotStateList<Book> = mutableStateListOf()
        private set

    var searching: MutableStateFlow<Boolean> =  MutableStateFlow(false)
        private set

    fun findBooks(keyword: String) {
        viewModelScope.launch {
            searching.value = true
            searchResults.clear()
            searchResults.addAll(sdk.getBookByTitle(keyword))
            searching.value = false
        }
    }

    fun addBook(book: Book) {
        sdk.addToBookshelf(book)
    }
}