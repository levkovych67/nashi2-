#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

case "${1:-}" in
  ""|--reset) ;;
  *) echo "Використання: $0 [--reset]" >&2; exit 2 ;;
esac

if [ "${1:-}" = "--reset" ]; then
  docker compose down -v
else
  docker compose down
fi
