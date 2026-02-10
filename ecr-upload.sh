#!/bin/sh
set -eu

# =============================
# CONFIG
# =============================
ROOT_DIR="${ROOT_DIR:-$(pwd)}"

AWS_REGION="${AWS_REGION:-ap-northeast-2}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

REPO_PREFIX="${REPO_PREFIX:-goorm}"
SERVICES_DIR="${SERVICES_DIR:-${ROOT_DIR}/service}"

PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"
BUILDER_NAME="${BUILDER_NAME:-multiarch}"
USE_CACHE="${USE_CACHE:-0}"

# ✅ OTel agent 버전(원하면 바꿔서 실행 가능)
OTEL_AGENT_VER="${OTEL_AGENT_VER:-2.20.1}"

# ✅ docker build 전에 gradle bootJar를 만들지 여부(기본 1)
BUILD_JAR="${BUILD_JAR:-1}"

# TAG: arg > git sha > timestamp
TAG="${1:-}"
if [ -z "$TAG" ]; then
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    TAG="$(git rev-parse --short HEAD)"
  else
    TAG="$(date +%Y%m%d%H%M%S)"
  fi
fi

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "❌ required command not found: $1"; exit 1; }
}

ensure_repo() {
  repo="$1"
  if ! aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$repo" >/dev/null 2>&1; then
    echo "🆕 creating ECR repo: $repo"
    aws ecr create-repository --region "$AWS_REGION" --repository-name "$repo" >/dev/null
  fi
}

ensure_buildx_builder() {
  docker buildx version >/dev/null 2>&1 || {
    echo "❌ docker buildx not available."
    exit 1
  }

  if ! docker buildx inspect "$BUILDER_NAME" >/dev/null 2>&1; then
    echo "🛠  creating buildx builder: $BUILDER_NAME"
    docker buildx create --name "$BUILDER_NAME" --use >/dev/null
  else
    docker buildx use "$BUILDER_NAME" >/dev/null
  fi

  docker buildx inspect --bootstrap >/dev/null
}

build_bootjar() {
  domain="$1"
  if [ "$BUILD_JAR" = "1" ]; then
    echo "🏗  Gradle bootJar: :service:${domain}:bootJar"
    # 루트에서 gradlew 실행된다는 전제 (ROOT_DIR 기준)
    (cd "$ROOT_DIR" && ./gradlew ":service:${domain}:bootJar" -x test)

    # 빌드 산출물 존재 확인
    if [ ! -d "${SERVICES_DIR}/${domain}/build/libs" ]; then
      echo "❌ build/libs not found: ${SERVICES_DIR}/${domain}/build/libs"
      exit 1
    fi
  fi
}

build_and_push_multiarch() {
  repo="$1"
  context_dir="$2"

  dockerfile="$context_dir/Dockerfile"
  if [ ! -f "$dockerfile" ]; then
    echo "❌ Dockerfile not found: $dockerfile"
    exit 1
  fi

  image_tag="${ECR_REGISTRY}/${repo}:${TAG}"
  image_latest="${ECR_REGISTRY}/${repo}:latest"

  echo "=============================="
  echo "🧱 Buildx Multi-Arch Build & Push"
  echo "  Repo     : $repo"
  echo "  Context  : $context_dir"
  echo "  Platforms: $PLATFORMS"
  echo "  Tag      : $TAG"
  echo "  OTelVer  : $OTEL_AGENT_VER"
  echo "=============================="

  if [ "$USE_CACHE" = "1" ]; then
    cache_ref="${ECR_REGISTRY}/${repo}:buildcache"
    docker buildx build \
      --platform "$PLATFORMS" \
      --provenance=false \
      --build-arg "OTEL_AGENT_VER=${OTEL_AGENT_VER}" \
      --cache-from "type=registry,ref=$cache_ref" \
      --cache-to "type=registry,ref=$cache_ref,mode=max" \
      -t "$image_tag" \
      -t "$image_latest" \
      --push \
      "$context_dir"
  else
    docker buildx build \
      --platform "$PLATFORMS" \
      --provenance=false \
      --build-arg "OTEL_AGENT_VER=${OTEL_AGENT_VER}" \
      -t "$image_tag" \
      -t "$image_latest" \
      --push \
      "$context_dir"
  fi
}

# =============================
# MAIN
# =============================
need_cmd aws
need_cmd docker

if [ ! -d "$SERVICES_DIR" ]; then
  echo "❌ services dir not found: $SERVICES_DIR"
  exit 1
fi

echo "🔐 ECR login: $ECR_REGISTRY ($AWS_REGION)"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null

ensure_buildx_builder

for d in order product payment user cart; do
  context="${SERVICES_DIR}/${d}"
  repo="${REPO_PREFIX}-${d}"

  if [ ! -d "$context" ]; then
    echo "⚠️ skip: domain dir not found: $context"
    continue
  fi

  # ✅ 도커 빌드 전에 jar 생성
  build_bootjar "$d"

  ensure_repo "$repo"
  build_and_push_multiarch "$repo" "$context"
done

echo "✅ DONE. tag=$TAG, platforms=$PLATFORMS"

