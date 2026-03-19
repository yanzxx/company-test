#!/usr/bin/env bash
set -euo pipefail

# 镜像仓库配置：可通过环境变量覆盖
REGISTRY_HOST="${REGISTRY_HOST:-crpi-ere0kjidm57ziagk.cn-beijing.personal.cr.aliyuncs.com}"
REGISTRY_NAMESPACE="${REGISTRY_NAMESPACE:-aitest_2}"
IMAGE_NAME="${IMAGE_NAME:-ai-pitch}"
IMAGE_TAG="${IMAGE_TAG:-last}"
REGISTRY_USERNAME="${REGISTRY_USERNAME:-dt_6368732917}"
REGISTRY_PASSWORD="${REGISTRY_PASSWORD:-Yanzx19930622my}"

FULL_IMAGE="${REGISTRY_HOST}/${REGISTRY_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"
IMAGE_PLATFORM="${IMAGE_PLATFORM:-linux/amd64}"

echo "[1/4] 登录阿里云镜像仓库: ${REGISTRY_HOST}"
echo "${REGISTRY_PASSWORD}" | docker login --username="${REGISTRY_USERNAME}" --password-stdin "${REGISTRY_HOST}"

echo "[2/4] 清理旧产物并本地重新构建前后端"
rm -rf snowy-web/dist
rm -f snowy-web-app/target/snowy-web-app-2.0.0.jar

echo "  - 前端构建：snowy-web npm run build"
(
  cd snowy-web
  npm set registry https://registry.npmmirror.com
  npm i
  npm run build
)

echo "  - 后端构建：mvn clean package"
mvn -s settings.xml -DskipTests clean package

if [[ ! -f "snowy-web/dist/index.html" ]]; then
  echo "前端 dist 构建失败：未找到 snowy-web/dist/index.html"
  exit 1
fi

if [[ ! -f "snowy-web-app/target/snowy-web-app-2.0.0.jar" ]]; then
  echo "后端 jar 构建失败：未找到 snowy-web-app/target/snowy-web-app-2.0.0.jar"
  exit 1
fi

echo "[3/4] 构建并推送单镜像(前端+后端): ${FULL_IMAGE}"
echo "构建平台: ${IMAGE_PLATFORM}"

if docker buildx version >/dev/null 2>&1; then
  docker buildx build \
    --platform "${IMAGE_PLATFORM}" \
    -t "${FULL_IMAGE}" \
    -f Dockerfile \
    --push \
    .
else
  echo "未检测到 docker buildx，回退到本机构建（可能导致跨架构不兼容）"
  docker build -t "${FULL_IMAGE}" -f Dockerfile .
  echo "[4/4] 推送镜像: ${FULL_IMAGE}"
  docker push "${FULL_IMAGE}"
fi

echo "完成: ${FULL_IMAGE}"
