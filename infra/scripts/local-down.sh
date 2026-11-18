#!/usr/bin/env bash
# Stops the local stack and keeps its data volumes. --volumes also deletes Mongo and MinIO data.
set -euo pipefail
cd "$(dirname "$0")/.."
args=()
if [[ "${1:-}" == "--volumes" ]]; then args+=(--volumes); fi
docker compose --profile web down ${args[@]+"${args[@]}"}
