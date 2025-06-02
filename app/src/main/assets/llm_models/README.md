---
frameworks:
- MNN
license: Apache License 2.0
tasks:
- text-generation

model-type:
- transformer

domain:
- nlp

language:
- cn

tags:
- instruction-tuned

tools:
- MNN
---

# Qwen3-0.6B-MNN

## 介绍（Introduction）
此模型是使用[llmexport](https://github.com/alibaba/MNN/tree/master/transformers/llm/export)从Qwen3-0.6B导出的4bit量化版本的MNN模型。

## 下载
```bash
#安装ModelScope
pip install modelscope
```
```bash
#命令行工具下载
modelscope download --model 'MNN/Qwen3-0.6B-MNN' --local_dir 'path/to/dir'
```
```python
#SDK模型下载
from modelscope import snapshot_download
model_dir = snapshot_download('MNN/Qwen3-0.6B-MNN')
```
Git下载
```bash
#Git模型下载
git clone https://www.modelscope.cn/MNN/Qwen3-0.6B-MNN.git
```

## 使用
```bash
# 下载MNN源码
git clone https://github.com/alibaba/MNN.git

# 编译
cd MNN
mkdir build && cd build
cmake .. -DMNN_LOW_MEMORY=true -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true -DMNN_BUILD_LLM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true
make -j

# 运行
./llm_demo /path/to/Qwen3-0.6B-MNN/config.json prompt.txt
```

## 文档
[MNN-LLM](https://mnn-docs.readthedocs.io/en/latest/transformers/llm.html#)
    