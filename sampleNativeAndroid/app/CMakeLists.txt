# set the required cmake version for this project
cmake_minimum_required( VERSION 3.6 )

# set the language level of C++-Files and C-Files
set( CMAKE_C_STANDARD 99 )
set( CMAKE_CXX_STANDARD 11 )

#set( CMAKE_C_STANDARD "C:/MinGW/bin/mingw32-gcc.exe" )
#set( CMAKE_CXX_STANDARD "C:/MinGW/bin/mingw32-g++.exe" ) 

set( CMAKE_MAKE_PROGRAM Ninja )
#set( CMAKE_MAKE_PROGRAM "C:/Users/asalvaterra/AppData/Local/Android/Sdk/cmake/3.10.2.4988404" )
 
add_definitions(-DTARGET_PLATFORM_ANDROID)

# set the path to the royale header-Files
include_directories( "${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/include" )

# set the path to the royale libraries
link_directories( "${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}" )

add_library( royaleSample SHARED

             # Provides a relative path to your source file(s).
             src/main/jni/royaleSample.cpp )

# set the target library to build and it's dependencies to be linked and compiled
target_link_libraries( royaleSample

                       # android libraries
                       android
                       log

                       # royale libraries
                       royale
                       spectre3
                       usb_android )
