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
import SwiftUI
import shared

class BookshelfViewModel : ObservableObject {
    private let sdk = BookshelfRepository()
    private var cancellable: LibraryCancellable?
    
    @Published var searching: Bool = false
    @Published var mySavedBooks = [Book]()
    @Published var bookSearchResults = [Book]()
    
    func startObservingSavedBooks() {
        self.cancellable = self.sdk.allBooksAsCallback(success: { data in
            self.mySavedBooks = data
        })
    }
    
    func stopObservingSavedBooks() {
        cancellable?.cancel()
    }
    
    func addBook(book: Book) {
        self.sdk.addToBookshelf(book: book)
    }
    
    func findBooks(title: String) {
        self.searching = true
        sdk.getBookByTitle(title: title, completionHandler:
            { result, error in
                if let errorBooks = error {
                    print(errorBooks.localizedDescription)
                    self.searching = false
                }
                if let resultBooks = result {
                    self.bookSearchResults = resultBooks
                    self.searching = false
                }
            })
    }
}

struct ContentView: View {
    @State private var selection = 0
    @State private var searchByTitle = ""
    @State private var searchText = ""
    @StateObject var viewModel = BookshelfViewModel()
    
    var body: some View {
        NavigationView {
            TabView(selection: $selection) {
                SearchScreen(selection: $selection, searchText: $searchText, viewModel: viewModel)
                    .tabItem {
                        Image(systemName: "house.fill")
                        Text("Home")
                    }
                    .tag(0)
                
                MySavedBooks(viewModel: viewModel)
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .tabItem {
                        Image(systemName: "bookmark.circle.fill")
                        Text("Books")
                    }
                    .tag(1)
                
                AboutScreen()
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .tabItem {
                        Image(systemName: "video.circle.fill")
                        Text("About")
                    }
                    .tag(2)
                
            }
            .onAppear() {
                UITabBar.appearance().barTintColor = .white
            }
            .navigationTitle("Bookshelf")
        }
    }
}

struct SearchScreen: View {
    @Binding var selection: Int
    @Binding var searchText: String
    @ObservedObject var viewModel: BookshelfViewModel
    
    var body: some View {
        VStack {
            SearchBar(text: $searchText, viewModel: viewModel)
            if (viewModel.searching) {
                ProgressView("🔎 Openlibrary.org…")
                    .frame(minWidth: 0, maxWidth: .infinity, minHeight: 0, maxHeight: .infinity)
            } else {
                List(viewModel.bookSearchResults, id: \.self) { book in
                    NavigationLink(
                        destination: HStack {
                            Text(book.title)
                            Button(action: {
                                selection = 1 // Navigate to Books
                                viewModel.addBook(book: book) // persist book
                            }, label: {
                                Text("Add")
                            })
                        },
                        label: {
                            Text(book.title)
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                        })
                }
            }
        }
    }
}

struct SearchBar: View {
    @Binding var text: String
    @State private var isEditing = false
    @ObservedObject var viewModel: BookshelfViewModel
    
    var body: some View {
        HStack {
            TextField("Search ...", text: $text)
                .padding(7)
                .padding(.horizontal, 25)
                .background(Color(.systemGray6))
                .cornerRadius(8)
                .overlay(
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.gray)
                            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                            .padding(.leading, 8)
                        
                        if isEditing {
                            Button(action: {
                                self.text = ""
                            }) {
                                Image(systemName: "multiply.circle.fill")
                                    .foregroundColor(.gray)
                                    .padding(.trailing, 8)
                            }
                        }
                    }
                )
                .padding(.horizontal, 10)
                .onTapGesture {
                    self.isEditing = true
                }
            
            if isEditing {
                Button(action: {
                    self.isEditing = false
                    viewModel.findBooks(title: self.text)
                    
                    self.text = ""
                    // Dismiss the keyboard
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }) {
                    Text("Find")
                }.disabled(self.text.isEmpty)
                .padding(.trailing, 10)
                .transition(.move(edge: .trailing))
                .animation(.default)
            }
        }
    }
}

struct MySavedBooks: View {
    @ObservedObject var viewModel: BookshelfViewModel
    
    var body: some View {
        NavigationView {
            VStack {
                List(viewModel.mySavedBooks, id: \.self) { book in
                    Text("Book: \(book.title)")
                }
                .onAppear {
                    viewModel.startObservingSavedBooks()
                }.onDisappear {
                    viewModel.stopObservingSavedBooks()
                }
            }
        }
    }
}

struct AboutScreen: View {
    @Environment(\.openURL) var openURL
    
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 25, style: .continuous)
                .fill(Color.gray)
            Button(action: {
                    openURL(URL(string: "https://www.github.com/realm/realm-kotlin")!)}, label: {
                        Text("""
Demo app using Realm-Kotlin Multiplatform SDK
                        
🎨 UI: using SwiftUI
---- Shared ---
📡 Network: using Ktor and Kotlinx.serialization
💾 Persistence: using Realm Database
""")
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.body)
                            .foregroundColor(.black)
                        
                    })
        }
        .padding(20)
        .multilineTextAlignment(.center)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
