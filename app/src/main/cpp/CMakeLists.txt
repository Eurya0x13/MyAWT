cmake_minimum_required(VERSION 3.22.1)

project("my_awt")

add_library(${CMAKE_PROJECT_NAME} SHARED
        jre_launcher.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

add_library("awt_xawt" SHARED awt_xawt.cpp)
target_link_options("awt_xawt" PRIVATE "-Wl,-z,max-page-size=16384")