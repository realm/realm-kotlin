cmake_minimum_required(VERSION 3.4.1)
project(RealmKotlin)
find_package(JNI)
set(SWIG_JNI "${CMAKE_SOURCE_DIR}/../../../jni-swig-stub/src/main/jni")

set(CMAKE_CXX_STANDARD 14)
add_subdirectory("${CMAKE_SOURCE_DIR}/../../../external/core" core EXCLUDE_FROM_ALL)

include_directories("${CMAKE_SOURCE_DIR}/core/src" "${JAVA_INCLUDE_PATH}" "${JAVA_INCLUDE_PATH}/darwin")
add_library(realmc SHARED ${SWIG_JNI}/realmc.cpp)
target_link_libraries(realmc RealmFFI)
