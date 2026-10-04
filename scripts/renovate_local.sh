#!/usr/bin/env bash
set -euo pipefail

readonly renovate_version="44.82.5"
readonly node_version="24.11.1"
readonly packages=(
  --yes
  --package "node@$node_version"
  --package "renovate@$renovate_version"
)

npx "${packages[@]}" -- renovate-config-validator --strict
LOG_LEVEL="${LOG_LEVEL:-info}" npx "${packages[@]}" -- renovate \
  --platform=local \
  --dry-run=lookup \
  --enabled-managers=gradle,gradle-wrapper,kotlin-script \
  "$@"
