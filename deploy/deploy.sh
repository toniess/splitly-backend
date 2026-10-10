#!/usr/bin/env bash
#
# Выкладка на сервер без реестра образов: собираем локально, переливаем по SSH.
#
#   ./deploy/deploy.sh user@1.2.3.4
#
# Первый запуск дольше: едет весь образ целиком (~150 МБ в сжатом виде).
# Дальше Docker передаёт только изменившиеся слои — обычно сотни килобайт.

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Использование: $0 user@host" >&2
    exit 1
fi

SERVER="$1"
REMOTE_DIR="/opt/splitly"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$PROJECT_ROOT"

echo "==> Тесты"
./gradlew test -q

echo "==> Сборка образа"
docker build -t splitly:prod .

echo "==> Передача образа на $SERVER"
docker save splitly:prod | gzip | ssh "$SERVER" 'gunzip | docker load'

echo "==> Передача конфигурации"
ssh "$SERVER" "mkdir -p $REMOTE_DIR"
scp deploy/docker-compose.prod.yml deploy/Caddyfile "$SERVER:$REMOTE_DIR/"

echo "==> Перезапуск"
# --no-deps, чтобы не трогать Caddy: его перезапуск заново запрашивает сертификат
ssh "$SERVER" "cd $REMOTE_DIR && docker compose -f docker-compose.prod.yml up -d"

echo "==> Ожидание готовности"
for attempt in $(seq 1 30); do
    status=$(ssh "$SERVER" "docker inspect -f '{{.State.Health.Status}}' splitly-app 2>/dev/null" || echo "unknown")
    if [ "$status" = "healthy" ]; then
        echo "Готово: https://api.toniess.dev"
        exit 0
    fi
    sleep 5
done

echo "Приложение не поднялось за 150 секунд. Логи:" >&2
ssh "$SERVER" "cd $REMOTE_DIR && docker compose -f docker-compose.prod.yml logs --tail 50 app" >&2
exit 1
