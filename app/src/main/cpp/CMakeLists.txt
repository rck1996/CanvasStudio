cmake_minimum_required(VERSION 3.22.1)
project(canvasstudio_vulkan LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_program(GLSLC glslc HINTS "${ANDROID_NDK}/shader-tools/${ANDROID_HOST_TAG}")
if(NOT GLSLC)
    message(FATAL_ERROR "glslc was not found in the Android NDK shader-tools directory")
endif()

set(SHADER_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/brush.comp")
set(SHADER_HEADER "${CMAKE_CURRENT_BINARY_DIR}/brush_comp.spv.h")
add_custom_command(
    OUTPUT "${SHADER_HEADER}"
    COMMAND "${GLSLC}" -O --target-env=vulkan1.1 -mfmt=c -fshader-stage=compute
            "${SHADER_SOURCE}" -o "${SHADER_HEADER}"
    DEPENDS "${SHADER_SOURCE}"
    VERBATIM
)
add_custom_target(canvasstudio_brush_shader DEPENDS "${SHADER_HEADER}")

add_library(canvasstudio_vulkan SHARED VulkanBrushRenderer.cpp "${SHADER_HEADER}")
add_dependencies(canvasstudio_vulkan canvasstudio_brush_shader)
target_include_directories(canvasstudio_vulkan PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")
target_compile_options(canvasstudio_vulkan PRIVATE -Wall -Wextra -Werror=return-type -Wno-missing-field-initializers)
target_link_libraries(canvasstudio_vulkan android log vulkan)
