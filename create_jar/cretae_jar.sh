#!/bin/bash
set -e

# 이 스크립트 파일이 있는 디렉터리
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 루트로 이동
cd "$SCRIPT_DIR/.."


./gradlew :service:user:bootJar

./gradlew :service:product:bootJar

./gradlew :service:payment:bootJar

./gradlew :service:order:bootJar

./gradlew :service:cart:bootJar

