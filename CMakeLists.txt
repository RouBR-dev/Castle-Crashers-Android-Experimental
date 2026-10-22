cmake_minimum_required(VERSION 3.25)
project(castle VERSION 0.1.0 LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android 15+ pode usar paginas de 16 KB.
if(ANDROID)
  add_link_options("-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
endif()

set(REXSDK_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/rexglue-sdk" CACHE PATH "rexglue SDK")
set(REXGLUE_ADRENOTOOLS_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/adrenotools" CACHE PATH "AdrenoTools source for Android custom Vulkan drivers")
set(REXGLUE_ADRENOTOOLS_JNI_OUTPUT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/android/app/src/main/jniLibs/arm64-v8a" CACHE PATH "Android JNI directory for AdrenoTools hook libraries")
set(CASTLE_GAME_DATA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/game" CACHE PATH "Pasta com default.xex")

file(TO_CMAKE_PATH "${CMAKE_CURRENT_SOURCE_DIR}" CASTLE_SOURCE_DIR_CONFIG)
file(TO_CMAKE_PATH "${CASTLE_GAME_DATA_ROOT}" CASTLE_GAME_DATA_ROOT_CONFIG)
file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/manifests")
configure_file(manifests/castle.toml.in
  "${CMAKE_CURRENT_BINARY_DIR}/manifests/castle.toml" @ONLY)

set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "" FORCE)
include(cmake/RexGlue.cmake)

# Codegen roda no PC (host), nao no Android.
add_custom_target(generate-castle
  COMMAND $<TARGET_FILE:rex::rexglue> codegen
          "${CMAKE_CURRENT_BINARY_DIR}/manifests/castle.toml"
  WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}"
  COMMENT "Recompilando default.xex -> C++"
  VERBATIM)

set(CASTLE_SOURCES src/main.cpp)
if(ANDROID)
  add_library(castle SHARED ${CASTLE_SOURCES})
else()
  add_executable(castle ${CASTLE_SOURCES})
endif()
target_include_directories(castle PRIVATE src "${CMAKE_CURRENT_SOURCE_DIR}")

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/generated/sources.cmake")
  include(generated/sources.cmake)
  target_sources(castle PRIVATE ${GENERATED_SOURCES})
  target_include_directories(castle PRIVATE generated)
else()
  message(STATUS "generated/sources.cmake ausente: rode o alvo generate-castle antes")
endif()

target_link_libraries(castle PRIVATE rex::runtime)
rexglue_configure_target(castle)
if(ANDROID)
  add_custom_command(TARGET castle POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory "${REXGLUE_ADRENOTOOLS_JNI_OUTPUT_DIR}"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "$<TARGET_FILE:castle>"
            "${REXGLUE_ADRENOTOOLS_JNI_OUTPUT_DIR}/libcastle.so"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "$<TARGET_FILE:rexruntime>"
            "${REXGLUE_ADRENOTOOLS_JNI_OUTPUT_DIR}/librexruntime.so"
    VERBATIM)
endif()
if(ANDROID AND CMAKE_BUILD_TYPE STREQUAL "Release")
  target_compile_options(castle PRIVATE -flto=thin)
  target_link_options(castle PRIVATE -flto=thin)
endif()
