#!/usr/bin/env bash
# Starts the local stack (mongo, minio, minio-init, backend). --with-web adds the web service (nginx on :4175).
set -euo pipefail
cd "$(dirname "$0")/.."
args=()
if [[ "${1:-}" == "--with-web" ]]; then args+=(--profile web); fi
docker compose ${args[@]+"${args[@]}"} up -d --build --wait
docker compose ${args[@]+"${args[@]}"} ps
echo "backend: http://127.0.0.1:${NASHI_BACKEND_PORT:-8081}/api/v2/health"
if [[ "${1:-}" == "--with-web" ]]; then echo "web: http://127.0.0.1:${NASHI_WEB_PORT:-4175}/"; fi
