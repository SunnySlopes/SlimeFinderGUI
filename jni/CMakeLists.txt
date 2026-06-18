cmake_minimum_required(VERSION 3.15)

project(slimeFinderLibJ LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

if(DEFINED JAVA_HOME)
    set(_JAVA_HOME "${JAVA_HOME}")
elseif(DEFINED ENV{JAVA_HOME})
    set(_JAVA_HOME "$ENV{JAVA_HOME}")
else()
    find_program(_JAVA_EXE java REQUIRED)
    get_filename_component(_JAVA_BIN "${_JAVA_EXE}" DIRECTORY)
    get_filename_component(_JAVA_HOME "${_JAVA_BIN}" DIRECTORY)
endif()

file(TO_CMAKE_PATH "${_JAVA_HOME}" _JAVA_HOME_CMAKE)

set(JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include")
if(WIN32)
    list(APPEND JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include/win32")
elseif(APPLE)
    list(APPEND JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include/darwin")
else()
    list(APPEND JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include/linux")
endif()

find_program(Java_JAVAC_EXECUTABLE javac
        HINTS "${_JAVA_HOME_CMAKE}/bin"
        REQUIRED
)

set(PROJECT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/..)
set(JAVA_BRIDGE_SRC ${PROJECT_ROOT}/src/main/java/com/sunnyslopes/slimefinder/SlimeFinderBridge.java)
set(JNI_GEN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/generated)
set(JNI_GEN_HEADER ${JNI_GEN_DIR}/com_sunnyslopes_slimefinder_SlimeFinderBridge.h)

if(NOT EXISTS "${PROJECT_ROOT}/cubiomes/finders.c")
    message(FATAL_ERROR "cubiomes not found at ${PROJECT_ROOT}/cubiomes — run: git submodule update --init --recursive")
endif()

add_custom_command(
        OUTPUT ${JNI_GEN_HEADER}
        COMMAND ${CMAKE_COMMAND} -E make_directory ${JNI_GEN_DIR}
        COMMAND ${Java_JAVAC_EXECUTABLE} -encoding UTF-8 -h ${JNI_GEN_DIR} ${JAVA_BRIDGE_SRC}
        DEPENDS ${JAVA_BRIDGE_SRC}
        COMMENT "Generating JNI header from SlimeFinderBridge.java"
        VERBATIM
)

add_custom_target(generate_jni_header DEPENDS ${JNI_GEN_HEADER})

set(SOURCES
        slime_finder_JNI.cpp
        ../cubiomes/finders.c
        ../cubiomes/generator.c
        ../cubiomes/layers.c
        ../cubiomes/biomenoise.c
        ../cubiomes/biomes.c
        ../cubiomes/noise.c
        ../cubiomes/util.c
        ../cubiomes/quadbase.c
        ../cubiomes/features/stronghold.c
)

add_library(slimeFinderLibJ SHARED ${SOURCES} ${JNI_GEN_HEADER})

add_dependencies(slimeFinderLibJ generate_jni_header)

target_include_directories(slimeFinderLibJ PRIVATE
        ${JNI_INCLUDE_DIRS}
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/..
        ../cubiomes
)

if(MSVC)
    target_compile_options(slimeFinderLibJ PRIVATE /O2 /DNDEBUG)
else()
    target_compile_options(slimeFinderLibJ PRIVATE -O3 -DNDEBUG -ffast-math)
endif()

if(MINGW)
    target_link_options(slimeFinderLibJ PRIVATE
            -static
            -static-libgcc
            -static-libstdc++
    )
endif()

set_target_properties(slimeFinderLibJ PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/native
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/native
)

if(NOT MSVC AND NOT WIN32)
    find_program(CMAKE_STRIP strip)
    if(CMAKE_STRIP)
        add_custom_command(TARGET slimeFinderLibJ POST_BUILD
                COMMAND ${CMAKE_STRIP} $<TARGET_FILE:slimeFinderLibJ>
        )
    endif()
endif()
