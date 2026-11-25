#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker не знайдено. Встановіть Docker Desktop." >&2; exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon не запущений. Запустіть Docker Desktop." >&2; exit 1
fi

case "${1:-}" in
  ""|--no-build) ;;
  *) echo "Використання: $0 [--no-build]" >&2; exit 2 ;;
esac

port_busy() { nc -z 127.0.0.1 "$1" >/dev/null 2>&1; }

RUNNING="$(docker compose ps --status running --services 2>/dev/null || true)"
is_running() { printf '%s\n' "$RUNNING" | grep -qx "$1"; }

if ! is_running backend && port_busy 8080; then
  echo "Порт 8080 зайнятий. Звільніть його і запустіть знову (цей порт фіксований)." >&2
  exit 1
fi
if ! is_running frontend && port_busy 4173; then
  echo "Порт 4173 зайнятий. Звільніть його і запустіть знову (цей порт фіксований)." >&2
  exit 1
fi

if [ -z "${NASHI_MONGO_PORT:-}" ]; then
  if is_running mongo; then
    CUR="$(docker compose port mongo 27017 2>/dev/null | sed 's/.*://' || true)"
    if [ -n "$CUR" ]; then
      export NASHI_MONGO_PORT="$CUR"
    fi
  fi
fi
if [ -z "${NASHI_MONGO_PORT:-}" ] && port_busy 27017; then
  for p in $(seq 27018 27030); do
    if ! port_busy "$p"; then
      export NASHI_MONGO_PORT="$p"
      echo "Порт 27017 зайнятий іншою MongoDB — використовую $p"
      break
    fi
  done
  if [ -z "${NASHI_MONGO_PORT:-}" ]; then
    echo "Не знайшов вільного порту для MongoDB серед 27017..27030." >&2; exit 1
  fi
fi

if [ -z "${VITE_MAPTILER_API_KEY:-}" ]; then
  ENV_FILE="../nashi-frontend/.env.local"
  if [ -f "$ENV_FILE" ]; then
    VITE_MAPTILER_API_KEY="$(grep -E '^VITE_MAPTILER_API_KEY=' "$ENV_FILE" | head -n1 | cut -d= -f2- || true)"
  fi
fi
if [ -n "${VITE_MAPTILER_API_KEY:-}" ]; then
  export VITE_MAPTILER_API_KEY
  echo "Ключ MapTiler: знайдено"
else
  export VITE_MAPTILER_API_KEY=""
  echo "Ключ MapTiler: не знайдено (карта може не завантажитись)"
fi

if [ "${1:-}" = "--no-build" ]; then
  docker compose up -d
else
  docker compose up -d --build
fi

echo "Чекаю на бекенд (до 180 с)..."
for i in $(seq 1 90); do
  if curl -fsS -o /dev/null http://localhost:8080/api/v1/health 2>/dev/null; then
    echo "Бекенд готовий."
    echo "Сайт: http://localhost:4173"
    echo "API: http://localhost:8080/swagger-ui"
    exit 0
  fi
  if [ -z "$(docker compose ps --status running --services 2>/dev/null | grep -x backend || true)" ]; then
    echo "Контейнер backend зупинився. Дивіться: docker compose logs backend" >&2
    exit 1
  fi
  sleep 2
done
echo "Бекенд не відповів за 180 с. Дивіться: docker compose logs backend" >&2
exit 1
