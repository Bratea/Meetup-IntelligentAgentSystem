#!/usr/bin/env bash
# =============================================================
# 寻晤智能agent系统 - Ollama 模型拉取脚本
# 用法: ./deploy/ollama/pull-models.sh
# =============================================================
set -euo pipefail

OLLAMA_HOST="${OLLAMA_HOST:-http://localhost:11434}"

echo "==> 检查 Ollama 服务: ${OLLAMA_HOST}"
until curl -sf "${OLLAMA_HOST}/api/tags" >/dev/null; do
  echo "    等待 Ollama 启动..."
  sleep 3
done

echo "==> 拉取向量模型 bge-m3 (约 2.3GB)"
ollama pull bge-m3

echo "==> 拉取对话模型 qwen2.5:7b (约 4.7GB)"
ollama pull qwen2.5:7b

echo "==> 当前模型列表"
ollama list

echo "==> 完成"
