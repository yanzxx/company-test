#!/usr/bin/env bash
set -euo pipefail

SERVER_HOST="${SERVER_HOST:-182.92.207.79}"
SERVER_USER="${SERVER_USER:-root}"
SERVER_PASSWORD="${SERVER_PASSWORD:-}"

REMOTE_ROOT="${REMOTE_ROOT:-/root/deploy/gago-fast-split}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-gago-fast-web:latest}"
BACKEND_IMAGE="${BACKEND_IMAGE:-gago-fast-server:latest}"
FRONTEND_CONTAINER="${FRONTEND_CONTAINER:-gago-fast-web}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-gago-fast-server}"
DOCKER_NETWORK="${DOCKER_NETWORK:-gago-fast-net}"
FRONTEND_PORT="${FRONTEND_PORT:-80}"
BACKEND_PORT="${BACKEND_PORT:-82}"
BACKEND_JAVA_OPTS="${BACKEND_JAVA_OPTS:--Xms512m -Xmx768m}"

if [[ -z "${SERVER_PASSWORD}" ]]; then
  echo "缺少 SERVER_PASSWORD 环境变量"
  exit 1
fi

if ! command -v sshpass >/dev/null 2>&1; then
  echo "缺少 sshpass，请先安装后重试"
  exit 1
fi

if [[ ! -f "snowy-web/dist/index.html" ]]; then
  echo "未找到 snowy-web/dist/index.html，请先确认本地前端 dist 已准备好"
  exit 1
fi

for required in \
  web.dist.remote.Dockerfile \
  server.remote.Dockerfile \
  pom.xml \
  settings.xml \
  snowy-web/etc/nginx/nginx.conf \
  snowy-web/etc/nginx/default.conf \
  snowy-web/etc/nginx/upstream.conf \
  snowy-web/etc/nginx/mime.types; do
  if [[ ! -e "${required}" ]]; then
    echo "缺少部署所需文件: ${required}"
    exit 1
  fi
done

TMP_DIR="$(mktemp -d)"
FRONTEND_TAR="${TMP_DIR}/frontend-dist.tar.gz"
BACKEND_TAR="${TMP_DIR}/backend-source.tar.gz"

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

echo "[1/5] 打包前端 dist 构建上下文"
tar -czf "${FRONTEND_TAR}" \
  web.dist.remote.Dockerfile \
  snowy-web/dist \
  snowy-web/etc/nginx/nginx.conf \
  snowy-web/etc/nginx/default.conf \
  snowy-web/etc/nginx/upstream.conf \
  snowy-web/etc/nginx/mime.types

echo "[2/5] 打包后端远程构建上下文"
tar \
  --exclude='*/target' \
  --exclude='*/node_modules' \
  --exclude='*/dist' \
  --exclude='*/.DS_Store' \
  --exclude='.git' \
  --exclude='.idea' \
  --exclude='app-log' \
  --exclude='snowy-web' \
  -czf "${BACKEND_TAR}" \
  server.remote.Dockerfile \
  pom.xml \
  settings.xml \
  snowy-common \
  snowy-plugin \
  snowy-plugin-api \
  snowy-web-app

echo "[3/5] 上传部署包到 ${SERVER_USER}@${SERVER_HOST}"
sshpass -p "${SERVER_PASSWORD}" ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_HOST}" "mkdir -p '${REMOTE_ROOT}/upload' '${REMOTE_ROOT}/logs'"
sshpass -p "${SERVER_PASSWORD}" scp -o StrictHostKeyChecking=no "${FRONTEND_TAR}" "${SERVER_USER}@${SERVER_HOST}:${REMOTE_ROOT}/upload/frontend-dist.tar.gz"
sshpass -p "${SERVER_PASSWORD}" scp -o StrictHostKeyChecking=no "${BACKEND_TAR}" "${SERVER_USER}@${SERVER_HOST}:${REMOTE_ROOT}/upload/backend-source.tar.gz"

echo "[4/5] 远程构建前后端镜像"
sshpass -p "${SERVER_PASSWORD}" ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_HOST}" \
  'bash -s' -- \
  "${REMOTE_ROOT}" \
  "${FRONTEND_IMAGE}" \
  "${BACKEND_IMAGE}" \
  "${FRONTEND_CONTAINER}" \
  "${BACKEND_CONTAINER}" \
  "${DOCKER_NETWORK}" \
  "${FRONTEND_PORT}" \
  "${BACKEND_PORT}" \
  "${BACKEND_JAVA_OPTS}" <<'EOSSH'
set -euo pipefail

REMOTE_ROOT="$1"
FRONTEND_IMAGE="$2"
BACKEND_IMAGE="$3"
FRONTEND_CONTAINER="$4"
BACKEND_CONTAINER="$5"
DOCKER_NETWORK="$6"
FRONTEND_PORT="$7"
BACKEND_PORT="$8"
BACKEND_JAVA_OPTS="$9"

FRONTEND_DIR="${REMOTE_ROOT}/frontend-context"
BACKEND_DIR="${REMOTE_ROOT}/backend-context"

rm -rf "${FRONTEND_DIR}" "${BACKEND_DIR}"
mkdir -p "${FRONTEND_DIR}" "${BACKEND_DIR}" "${REMOTE_ROOT}/logs"

tar -xzf "${REMOTE_ROOT}/upload/frontend-dist.tar.gz" -C "${FRONTEND_DIR}"
tar -xzf "${REMOTE_ROOT}/upload/backend-source.tar.gz" -C "${BACKEND_DIR}"

docker network create "${DOCKER_NETWORK}" >/dev/null 2>&1 || true

docker build --pull -t "${FRONTEND_IMAGE}" -f "${FRONTEND_DIR}/web.dist.remote.Dockerfile" "${FRONTEND_DIR}"
docker build --pull -t "${BACKEND_IMAGE}" -f "${BACKEND_DIR}/server.remote.Dockerfile" "${BACKEND_DIR}"

docker rm -f "${FRONTEND_CONTAINER}" >/dev/null 2>&1 || true
docker rm -f "${BACKEND_CONTAINER}" >/dev/null 2>&1 || true

docker run -d \
  --name "${BACKEND_CONTAINER}" \
  --restart unless-stopped \
  --network "${DOCKER_NETWORK}" \
  -p "${BACKEND_PORT}:82" \
  -v "${REMOTE_ROOT}/logs:/app/app-log" \
  --entrypoint sh \
  "${BACKEND_IMAGE}" \
  -c "exec java ${BACKEND_JAVA_OPTS} -jar /app/gago-snowy.jar"

docker run -d \
  --name "${FRONTEND_CONTAINER}" \
  --restart unless-stopped \
  --network "${DOCKER_NETWORK}" \
  -p "${FRONTEND_PORT}:80" \
  "${FRONTEND_IMAGE}"

echo "containers_started"
docker ps --filter "name=${FRONTEND_CONTAINER}" --filter "name=${BACKEND_CONTAINER}"
EOSSH

echo "[5/5] 验证服务"
sshpass -p "${SERVER_PASSWORD}" ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${SERVER_HOST}" \
  'bash -s' -- \
  "${FRONTEND_CONTAINER}" \
  "${BACKEND_CONTAINER}" \
  "${FRONTEND_PORT}" \
  "${BACKEND_PORT}" <<'EOSSH'
set -euo pipefail

FRONTEND_CONTAINER="$1"
BACKEND_CONTAINER="$2"
FRONTEND_PORT="$3"
BACKEND_PORT="$4"

sleep 12

echo "--- frontend ---"
curl -s -I "http://127.0.0.1:${FRONTEND_PORT}/" | sed -n '1,5p'

echo "--- backend ---"
backend_status="$(curl -s -o /tmp/gago_backend_check.txt -w '%{http_code}' "http://127.0.0.1:${BACKEND_PORT}/auth/b/getPicCaptcha" || true)"
echo "backend_http_status=${backend_status}"
if [[ -f /tmp/gago_backend_check.txt ]]; then
  sed -n '1,3p' /tmp/gago_backend_check.txt || true
fi

echo "--- backend logs ---"
docker logs --tail 40 "${BACKEND_CONTAINER}" 2>&1 | sed -n '1,80p'
EOSSH

echo "部署完成"
echo "前端地址: http://${SERVER_HOST}:${FRONTEND_PORT}"
echo "后端地址: http://${SERVER_HOST}:${BACKEND_PORT}"
