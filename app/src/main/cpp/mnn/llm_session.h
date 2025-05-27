//
// llm_session.h
// 神迹输入法专用的简化LLM会话管理
//

#pragma once
#include <vector>
#include <string>
#include <memory>

class LlmSession {
public:
    LlmSession();
    ~LlmSession();
    
    // 初始化模型
    bool init(const std::string& config_path, const std::string& model_dir);
    
    // 生成文本
    std::string generate(const std::string& input, int max_tokens = 128);
    
    // 重置会话
    void reset();
    
    // 检查是否已加载
    bool isLoaded() const;
    
    // 获取模型信息
    std::string getModelInfo() const;
    
private:
    bool loaded_;
    std::string model_path_;
    std::string config_path_;
    int max_tokens_;
    
    // 简单的文本生成模拟（暂时用于测试）
    std::string simulateGeneration(const std::string& input, int max_tokens);
};

