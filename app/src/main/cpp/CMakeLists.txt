# CMakeLists.txt for Shenji Keyboard MNN LLM Integration

cmake_minimum_required(VERSION 3.22.1)

project("shenjillm")

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加源文件
add_library(${CMAKE_PROJECT_NAME} SHARED
    mnn/shenji_llm_jni.cpp
    mnn/llm_session.cpp
)

# 设置包含目录
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    mnn/include
)

# 查找系统库
find_library(log-lib log)

# 链接库
target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
    android
) 