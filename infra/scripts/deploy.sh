#!/usr/bin/env bash
# Deploys the images tagged <tag> on this server (the pipeline runs it over SSH; it also works by hand):
#   infra/scripts/deploy.sh <tag>
# Pulls backend and web, recreates the stack, waits until every container is healthy, then records the tag in
# infra/.env (DEPLOY_IMAGE_TAG, so a manual "docker compose up" runs the same images) and infra/.last-good-tag.
# On failure it starts the last good tag again and exits 1.
# DEPLOY_SKIP_PULL=1 uses images already on this machine (built with --build, or for a local try).
set -euo pipefail
cd "$(dirname "$0")/.."
tag="${1:?usage: infra/scripts/deploy.sh <image tag>}"
[[ -f .env ]] || { echo "infra/.env is missing: copy infra/.env.example and fill it in (infra/DEPLOY.md)" >&2; exit 1; }

compose() { DEPLOY_IMAGE_TAG="$1" docker compose -f docker-compose.prod.yml "${@:2}"; }

start() {
  if [[ "${DEPLOY_SKIP_PULL:-}" != 1 ]]; then compose "$1" pull --quiet backend web || return 1; fi
  compose "$1" up -d --no-build --pull never --wait --wait-timeout "${DEPLOY_WAIT_SECONDS:-180}"
}

record() {
  local tmp
  tmp="$(mktemp .env.XXXXXX)"
  grep -v '^DEPLOY_IMAGE_TAG=' .env > "$tmp" || true
  echo "DEPLOY_IMAGE_TAG=$1" >> "$tmp"
  chmod 600 "$tmp"
  mv "$tmp" .env
  echo "$1" > .last-good-tag
}

previous="$(cat .last-good-tag 2>/dev/null || true)"
if start "$tag"; then
  record "$tag"
  echo "Deployed $tag"
  exit 0
fi
echo "Deploy of $tag failed" >&2
compose "$tag" ps -a >&2 || true
if [[ -n "$previous" && "$previous" != "$tag" ]]; then
  echo "Rolling back to $previous" >&2
  if start "$previous"; then echo "Rolled back to $previous" >&2; else echo "Rollback to $previous failed too" >&2; fi
fi
exit 1
