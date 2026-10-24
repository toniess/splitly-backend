#!/usr/bin/env bash
#
# Сборка образа под Container Apps и отправка в Artifact Registry.
#
#   ./deploy/build-and-push.sh <адрес-реестра> [тег]
#   ./deploy/build-and-push.sh toniess.cr.cloud.ru
#
# Перед первым запуском нужно войти в реестр:
#   docker login <адрес-реестра>

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Использование: $0 <адрес-реестра> [тег]" >&2
    exit 1
fi

REGISTRY="$1"
TAG="${2:-$(git rev-parse --short HEAD)}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$PROJECT_ROOT"

echo "==> Тесты"
./gradlew test -q

echo "==> Сборка под linux/amd64, тег $TAG"
# --platform обязателен: на Apple Silicon сборка по умолчанию даёт arm64,
# а облако запускает amd64 — контейнер упал бы с exec format error.
docker buildx build \
    --platform linux/amd64 \
    --tag "$REGISTRY/splitly:$TAG" \
    --tag "$REGISTRY/splitly:latest" \
    --push \
    .

echo
echo "Готово. В Container Apps создай ревизию с образом:"
echo "    $REGISTRY/splitly:$TAG"
