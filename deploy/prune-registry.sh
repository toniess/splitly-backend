#!/usr/bin/env bash
#
# Удаляет из репозитория реестра все образы, кроме перечисленных тегов.
#
#   prune-registry.sh <реестр> <репозиторий> <тег-который-оставить>...
#   prune-registry.sh splitly.cr.cloud.ru splitly sha-a1b2c3d sha-9f8e7d6 latest
#
# Учётные данные берутся из переменных окружения:
#   REGISTRY_USERNAME  — Key ID реестра
#   REGISTRY_PASSWORD  — Key Secret реестра
#
# Пробный прогон без удаления:
#   DRY_RUN=1 prune-registry.sh ...
#
# Намеренно обходится без mapfile и declare -A: в macOS штатный bash версии 3.2,
# и скрипт должен работать и там, и на раннере.

set -euo pipefail

if [ $# -lt 2 ]; then
    echo "Использование: $0 <реестр> <репозиторий> <тег>..." >&2
    exit 1
fi

REGISTRY="$1"; shift
REPOSITORY="$1"; shift

: "${REGISTRY_USERNAME:?не задан REGISTRY_USERNAME}"
: "${REGISTRY_PASSWORD:?не задан REGISTRY_PASSWORD}"

DRY_RUN="${DRY_RUN:-0}"
# http нужен только для прогона против локального реестра в тестах
BASE="${REGISTRY_SCHEME:-https}://${REGISTRY}/v2/${REPOSITORY}"

# Типы манифестов, которые должен вернуть реестр. Без этого заголовка реестр
# может отдать манифест другой схемы, и дайджест в ответе не совпадёт с тем,
# по которому образ удаляется.
ACCEPT='application/vnd.oci.image.index.v1+json,application/vnd.oci.image.manifest.v1+json,application/vnd.docker.distribution.manifest.list.v2+json,application/vnd.docker.distribution.manifest.v2+json'

api() {
    curl -sS --fail-with-body -u "${REGISTRY_USERNAME}:${REGISTRY_PASSWORD}" "$@"
}

# Дайджест тега. Пустая строка, если тега нет.
#
# `|| true` обязателен: на отсутствующий тег реестр отвечает 404, curl выходит
# с ошибкой, и под `set -e` это уронило бы весь скрипт. А отсутствие тега —
# нормальная ситуация: предыдущего образа может не быть в реестре.
digest_of() {
    { api -I -H "Accept: ${ACCEPT}" "${BASE}/manifests/$1" 2>/dev/null || true; } \
        | tr -d '\r' \
        | awk -F': ' 'tolower($1) == "docker-content-digest" { print $2 }'
}

echo "==> Список тегов в ${REGISTRY}/${REPOSITORY}"
all_tags="$(api "${BASE}/tags/list" | jq -r '.tags[]? // empty')"

if [ -z "$all_tags" ]; then
    echo "    репозиторий пуст, удалять нечего"
    exit 0
fi

echo "    найдено тегов: $(echo "$all_tags" | wc -l | tr -d ' ')"

# Сначала собираем дайджесты, которые трогать нельзя.
#
# Сравнение идёт по дайджестам, а не по именам тегов: если два коммита дали
# побайтово одинаковый образ, манифест у них общий. Удаление «старого» тега
# по дайджесту снесло бы и новый вместе с ним.
keep_digests=""
for tag in "$@"; do
    digest="$(digest_of "$tag")"
    if [ -n "$digest" ]; then
        keep_digests="${keep_digests}${digest}
"
        echo "    оставляем ${tag} (${digest:0:19}...)"
    else
        echo "    тега ${tag} в реестре нет, пропускаем"
    fi
done

if [ -z "$keep_digests" ]; then
    echo "Ни один из указанных тегов не найден — удаление отменено." >&2
    echo "Иначе репозиторий вычистился бы целиком." >&2
    exit 1
fi

echo "==> Удаление остальных"
deleted=0
while IFS= read -r tag; do
    [ -z "$tag" ] && continue

    digest="$(digest_of "$tag")"
    [ -z "$digest" ] && continue
    if printf '%s' "$keep_digests" | grep -qxF "$digest"; then
        continue
    fi

    if [ "$DRY_RUN" = "1" ]; then
        echo "    [пробный прогон] удалил бы ${tag}"
    else
        echo "    удаляем ${tag}"
        api -X DELETE "${BASE}/manifests/${digest}" >/dev/null
    fi
    deleted=$((deleted + 1))
done <<EOF
$all_tags
EOF

if [ "$deleted" -eq 0 ]; then
    echo "    лишних образов нет"
else
    echo "==> Удалено: ${deleted}"
fi

# Место освобождается не сразу: слои общие между образами, и реестр проверяет,
# не используется ли образ работающим контейнером.
