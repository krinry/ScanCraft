cmake_minimum_required(VERSION 3.22.1)

project("scancraft")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Aggressive compiler optimization flags for commercial-grade speed
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG -flto -ffast-math -fvisibility=hidden")

if(ANDROID_ABI MATCHES "arm64-v8a")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -march=armv8-a+simd")
endif()

set(THORVG_DIR "${CMAKE_CURRENT_SOURCE_DIR}/thorvg")

file(GLOB THORVG_COMMON_SRCS "${THORVG_DIR}/src/common/*.cpp")
file(GLOB THORVG_RENDERER_SRCS "${THORVG_DIR}/src/renderer/*.cpp")
file(GLOB THORVG_CPU_SRCS "${THORVG_DIR}/src/renderer/cpu_engine/*.cpp")
file(GLOB THORVG_SVG_SRCS "${THORVG_DIR}/src/loaders/svg/*.cpp")
file(GLOB THORVG_RAW_SRCS "${THORVG_DIR}/src/loaders/raw/*.cpp")

set(THORVG_SRCS
    ${THORVG_COMMON_SRCS}
    ${THORVG_RENDERER_SRCS}
    ${THORVG_CPU_SRCS}
    ${THORVG_SVG_SRCS}
    ${THORVG_RAW_SRCS}
)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${THORVG_DIR}
    ${THORVG_DIR}/inc
    ${THORVG_DIR}/src/common
    ${THORVG_DIR}/src/renderer
    ${THORVG_DIR}/src/renderer/cpu_engine
    ${THORVG_DIR}/src/loaders/svg
    ${THORVG_DIR}/src/loaders/raw
)

add_library(
    scancraft
    SHARED
    native_scanner.cpp
    edge_detector.cpp
    perspective_warp.cpp
    magic_color.cpp
    thorvg_bridge.cpp
    ${THORVG_SRCS}
)

find_library(
    log-lib
    log
)

find_library(
    jnigraphics-lib
    jnigraphics
)

target_link_libraries(
    scancraft
    ${log-lib}
    ${jnigraphics-lib}
)
