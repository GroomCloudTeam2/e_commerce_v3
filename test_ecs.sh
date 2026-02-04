#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${1:-}"   # cart, order, payment, product, user
TAG="${2:-}"            # optional (e.g. git sha). default: git sha or timestamp

if [[ -z "${SERVICE_NAME}" ]]; then
  echo "Usage: ./test_ecs.sh <service_name> [tag]"
  echo "Example: ./test_ecs.sh cart"
  echo "Example: ./test_ecs.sh cart 1a2b3c4"
  exit 1
fi

PROJECT_ROOT="$(pwd)"
SERVICE_DIR="${PROJECT_ROOT}/service/${SERVICE_NAME}"

REGION="ap-northeast-2"
ACCOUNT_ID="900808296075"
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
ECR_REPO="${ECR_REGISTRY}/goorm-${SERVICE_NAME}"

CLUSTER="earnest-cat-gtzzrw"
TASK_DEF_NAME="msa-${SERVICE_NAME}"

# ✅ ECS가 linux/amd64(x86_64)로 뜨는 경우가 대부분이라 기본값을 amd64로.
# 멀티아치로 올리고 싶으면 실행할 때:
#   PLATFORMS=linux/amd64,linux/arm64 ./test_ecs.sh cart
PLATFORMS="${PLATFORMS:-linux/amd64}"

# 태그 자동 생성 (git 가능하면 git sha, 아니면 timestamp)
if [[ -z "${TAG}" ]]; then
  if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    TAG="$(git rev-parse --short HEAD)"
  else
    TAG="$(date +%Y%m%d%H%M%S)"
  fi
fi

# jq 체크
if ! command -v jq >/dev/null 2>&1; then
  echo "❌ jq is required. (brew install jq)"
  exit 1
fi

# ECS 서비스 이름 찾기
ECS_SERVICE="$(
  aws ecs list-services \
    --cluster "${CLUSTER}" \
    --region "${REGION}" \
    --query "serviceArns[?contains(@, 'msa-${SERVICE_NAME}-service')]" \
    --output text \
  | xargs -n1 basename \
  | head -n1
)"

if [[ -z "${ECS_SERVICE}" ]]; then
  echo "❌ ECS Service not found for service_name='${SERVICE_NAME}' (cluster=${CLUSTER})"
  exit 1
fi

echo "=========================================="
echo "Service          : ${SERVICE_NAME}"
echo "ECR Repo         : ${ECR_REPO}"
echo "Image Tag        : ${TAG}"
echo "Platforms        : ${PLATFORMS}"
echo "Task Definition  : ${TASK_DEF_NAME}"
echo "ECS Service      : ${ECS_SERVICE}"
echo "=========================================="

# 1) ECR 로그인
echo "🔐 ECR Login..."
aws ecr get-login-password --region "${REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

# 2) Gradle 빌드
echo "🔨 Building ${SERVICE_NAME}..."
./gradlew ":service:${SERVICE_NAME}:bootJar"

# 3) Docker buildx 빌드 & 푸시 (linux/amd64로 강제)
echo "🐳 Docker buildx build & push..."
BUILDER_NAME="ecs-builder"

if ! docker buildx inspect "${BUILDER_NAME}" >/dev/null 2>&1; then
  docker buildx create --name "${BUILDER_NAME}" --use >/dev/null
else
  docker buildx use "${BUILDER_NAME}" >/dev/null
fi
docker buildx inspect --bootstrap >/dev/null

# buildx는 로컬에 이미지가 없어도 --push로 바로 ECR로 올림
docker buildx build \
  --platform "${PLATFORMS}" \
  -t "${ECR_REPO}:${TAG}" \
  -t "${ECR_REPO}:latest" \
  --push \
  "${SERVICE_DIR}"

NEW_IMAGE="${ECR_REPO}:${TAG}"
echo "✅ Pushed image: ${NEW_IMAGE}"

# 4) 현재 Task Definition 가져오기
echo "📋 Fetching current task definition..."
aws ecs describe-task-definition \
  --task-definition "${TASK_DEF_NAME}" \
  --region "${REGION}" \
  --query 'taskDefinition' > /tmp/task-def.json

# 5) 새 Task Definition JSON 만들기 (불필요 필드 제거 + image 치환)
echo "📝 Preparing new task definition revision..."
jq --arg IMAGE "${NEW_IMAGE}" --arg REPO "${ECR_REPO}" '
  del(
    .taskDefinitionArn,
    .revision,
    .status,
    .requiresAttributes,
    .compatibilities,
    .registeredAt,
    .registeredBy
  )
  | .containerDefinitions |= (
      if (map(.image | contains($REPO)) | any) then
        map(if (.image | contains($REPO)) then .image = $IMAGE else . end)
      else
        (.[0].image = $IMAGE)
      end
    )
' /tmp/task-def.json > /tmp/new-task-def.json

NEW_TASK_DEF_ARN="$(
  aws ecs register-task-definition \
    --cli-input-json file:///tmp/new-task-def.json \
    --region "${REGION}" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text
)"

echo "✅ New Task Definition: ${NEW_TASK_DEF_ARN}"

# 6) ECS Service 업데이트
echo "🚀 Updating ECS service..."
aws ecs update-service \
  --cluster "${CLUSTER}" \
  --service "${ECS_SERVICE}" \
  --task-definition "${NEW_TASK_DEF_ARN}" \
  --force-new-deployment \
  --region "${REGION}" >/dev/null

echo "✅ Deployment triggered!"
echo "📊 Check status:"
echo "aws ecs describe-services --cluster ${CLUSTER} --services ${ECS_SERVICE} --region ${REGION}"

