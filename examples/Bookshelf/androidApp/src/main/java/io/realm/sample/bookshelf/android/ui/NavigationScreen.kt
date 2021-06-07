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

import androidx.annotation.StringRes
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import io.realm.sample.bookshelf.android.R

sealed class NavigationScreens(
    val name: String,
    @StringRes val resourceId: Int,
    val icon: ImageVector
) {
    companion object {
        fun fromRoute(route: String?): NavigationScreens =
            when (route?.substringBefore("/")) {
                Search.name -> Search
                Books.name -> Books
                About.name -> About
                null -> Search
                else -> throw IllegalArgumentException("Route $route is not recognized.")
            }
    }

    object Search :
        NavigationScreens("Search", R.string.search_screen_route, Icons.Filled.Search)

    object Books : NavigationScreens("Books", R.string.books_screen_route, Icons.Filled.List)
    object About : NavigationScreens("About", R.string.about_screen_route, Icons.Filled.Home)
}

@Composable
fun bottomNavigation(
    navController: NavHostController,
    items: List<NavigationScreens>
) {
    val backstackEntry = navController.currentBackStackEntryAsState()
    val currentScreen = NavigationScreens.fromRoute(backstackEntry.value?.destination?.route)

    BottomNavigation {
        items.forEach { screen ->
            BottomNavigationItem(
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(stringResource(id = screen.resourceId)) },
                selected = currentScreen.name == screen.name,
                onClick = {
                    navController.navigate(screen.name)
                }
            )
        }
    }
}


@ExperimentalComposeUiApi
@Composable
fun bookshelfNavHost(
    bookshelfViewModel: BookshelfViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController,
        startDestination = NavigationScreens.About.name,
        modifier = modifier
    ) {
        composable(NavigationScreens.Search.name) {
            searchScreen(bookshelfViewModel.searchResults,
                bookshelfViewModel.searching,
                navController,
                bookshelfViewModel::findBooks, bookshelfViewModel::addBook)
        }
        // FIXME This doesn't bring saved Books to Realm
        composable(NavigationScreens.Books.name) {
            savedBooks(bookshelfViewModel.savedBooks)
        }
        composable(NavigationScreens.About.name) {
            aboutScreen()
        }
    }
}