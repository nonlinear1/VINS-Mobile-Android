cmake_minimum_required(VERSION 3.4.1)

add_subdirectory(GeographicLib)

set(TARGET_NAME Global)
file(GLOB_RECURSE CPP_SRCS "${CMAKE_CURRENT_SOURCE_DIR}/*.cpp" "${CMAKE_CURRENT_SOURCE_DIR}*.hpp")

add_library(${TARGET_NAME} STATIC ${CPP_SRCS})

target_link_libraries(${TARGET_NAME} eigen_LIB)
target_link_libraries(${TARGET_NAME} VINSMobile)
target_link_libraries(${TARGET_NAME} libGeographiccc)

target_include_directories(${TARGET_NAME} INTERFACE ${CMAKE_CURRENT_SOURCE_DIR})