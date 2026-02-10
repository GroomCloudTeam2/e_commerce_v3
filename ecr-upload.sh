#!/bin/sh
set -eu

# =============================
# CONFIG
# =============================
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

REPO_PREFIX="${REPO_PREFIX:-goorm}"           # goorm-order 처럼 prefix
SERVICES_DIR="${SERVICES_DIR:-$(pwd)/service}" # 모노레포 루트에서 실행 기준

# 멀티 아키텍처 플랫폼
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"

# buildx builder
BUILDER_NAME="${BUILDER_NAME:-multiarch}"

# (옵션) registry cache 사용(빠르게 빌드): 1이면 cache-to/cache-from 사용
USE_CACHE="${USE_CACHE:-0}"

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
  # buildx 존재 확인
  docker buildx version >/dev/null 2>&1 || {
    echo "❌ docker buildx not available. (Docker Desktop 최신/Buildx 플러그인 필요)"
    exit 1
  }

  # builder가 없으면 생성
  if ! docker buildx inspect "$BUILDER_NAME" >/dev/null 2>&1; then
    echo "🛠  creating buildx builder: $BUILDER_NAME"
    docker buildx create --name "$BUILDER_NAME" --use >/dev/null
  else
    docker buildx use "$BUILDER_NAME" >/dev/null
  fi

  # builder 부트스트랩
  docker buildx inspect --bootstrap >/dev/null
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
  echo "=============================="

  # (옵션) 캐시 사용
  if [ "$USE_CACHE" = "1" ]; then
    cache_ref="${ECR_REGISTRY}/${repo}:buildcache"
    docker buildx build \
      --platform "$PLATFORMS" \
      --provenance=false \
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

# 고정 도메인 5개
for d in order product payment user cart; do
  context="${SERVICES_DIR}/${d}"
  repo="${REPO_PREFIX}-${d}"

  if [ ! -d "$context" ]; then
    echo "⚠️ skip: domain dir not found: $context"
    continue
  fi

  ensure_repo "$repo"
  build_and_push_multiarch "$repo" "$context"
done

echo "✅ DONE. tag=$TAG, platforms=$PLATFORMS"

