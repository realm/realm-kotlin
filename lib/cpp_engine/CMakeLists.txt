cmake_minimum_required(VERSION 3.0)

project(realm-objectstore-wrapper)

set(CMAKE_BUILD_TYPE Debug)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED on)
set(CMAKE_CXX_EXTENSIONS off)
add_compile_options(-Wall -DREALM_HAVE_CONFIG)
add_compile_options("$<$<CONFIG:DEBUG>:-DREALM_DEBUG>")
add_link_options(-Wl,-all_load)

add_subdirectory(external/realm-object-store)
include_directories(external/realm-object-store/external/json)

add_library(realm-objectstore-wrapper STATIC
  database.cpp
  wrapper.cpp
)

file(STRINGS "${CMAKE_SOURCE_DIR}/external/realm-object-store/dependencies.list" DEPENDENCIES)
foreach(LINE IN LISTS DEPENDENCIES)
    string(REGEX MATCHALL "([^=]+)" KEY_VALUE ${LINE})
    list(LENGTH KEY_VALUE matches_count)
    if(matches_count STREQUAL 2)
        list(GET KEY_VALUE 0 KEY)
        list(GET KEY_VALUE 1 VALUE)
        set(DEP_${KEY} ${VALUE})
    endif()
endforeach()

target_link_libraries(realm-objectstore-wrapper realm-object-store ${CMAKE_CURRENT_SOURCE_DIR}/external/realm-object-store/CMakeFiles/realm-core-${DEP_REALM_CORE_VERSION}/librealm-parser-macosx.a)
