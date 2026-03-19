#!/usr/bin/env bash
set -euo pipefail

# 服务器配置：可通过环境变量覆盖
SERVER_HOST="${SERVER_HOST:-47.254.16.143}"
SERVER_USER="${SERVER_USER:-root}"
SERVER_PASSWORD="${SERVER_PASSWORD:-Song126886}"

# 镜像配置：可通过环境变量覆盖
REGISTRY_HOST="${REGISTRY_HOST:-crpi-ere0kjidm57ziagk.cn-beijing.personal.cr.aliyuncs.com}"
REGISTRY_NAMESPACE="${REGISTRY_NAMESPACE:-aitest_2}"
IMAGE_NAME="${IMAGE_NAME:-ai-pitch}"
IMAGE_TAG="${IMAGE_TAG:-last}"
REGISTRY_USERNAME="${REGISTRY_USERNAME:-dt_6368732917}"
REGISTRY_PASSWORD="${REGISTRY_PASSWORD:-Yanzx19930622my}"
CONTAINER_NAME="${CONTAINER_NAME:-ai-pitch}"
FRONTEND_PORT="${FRONTEND_PORT:-80}"
BACKEND_PORT="${BACKEND_PORT:-82}"

FULL_IMAGE="${REGISTRY_HOST}/${REGISTRY_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"

if ! command -v sshpass >/dev/null 2>&1; then
  echo "缺少 sshpass，请先安装：macOS 可执行 brew install hudochenkov/sshpass/sshpass"
  exit 1
fi

echo "开始远程部署到 ${SERVER_USER}@${SERVER_HOST}"
sshpass -p "${SERVER_PASSWORD}" ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_HOST}" bash <<EOSSH
set -euo pipefail

echo "[远程 1/5] 登录阿里云镜像仓库"
echo "${REGISTRY_PASSWORD}" | docker login --username="${REGISTRY_USERNAME}" --password-stdin "${REGISTRY_HOST}"

echo "[远程 2/5] 拉取镜像 ${FULL_IMAGE}"
docker pull "${FULL_IMAGE}"

echo "[远程 3/5] 停止并删除旧容器(如存在)"
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

echo "[远程 4/5] 启动新容器"
# 端口占用时自动回退，避免部署中断
frontend_port="${FRONTEND_PORT}"
backend_port="${BACKEND_PORT}"
if ss -lnt | awk '{print \$4}' | grep -E "(:|\\*)${FRONTEND_PORT}\$" >/dev/null 2>&1; then
  echo "检测到 ${FRONTEND_PORT} 端口被占用，前端端口回退到 8080"
  frontend_port="8080"
fi
if ss -lnt | awk '{print \$4}' | grep -E "(:|\\*)${BACKEND_PORT}\$" >/dev/null 2>&1; then
  echo "检测到 ${BACKEND_PORT} 端口被占用，后端端口回退到 8082"
  backend_port="8082"
fi

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p \${frontend_port}:80 \
  -p \${backend_port}:82 \
  -v /data/${CONTAINER_NAME}/app-log:/app-log \
  "${FULL_IMAGE}"

echo "[远程 5/5] 查看容器状态"
docker ps --filter "name=${CONTAINER_NAME}"
echo "访问地址: http://${SERVER_HOST}:\${frontend_port}"
echo "后端地址: http://${SERVER_HOST}:\${backend_port}"
EOSSH

echo "远程部署完成"
