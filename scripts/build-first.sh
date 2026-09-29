#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

if ! command -v python3 >/dev/null 2>&1; then
  echo "Required command is not available: python3" >&2
  exit 127
fi

echo "[1/4] Validate POM structure and dependency-management boundaries"
python3 scripts/validate-poms.py

echo "[2/4] Validate direct source dependencies"
python3 scripts/check-source-dependencies.py

echo "[3/4] Validate component-specific static invariants"
python3 component/retry-component/scripts/verify-package-layout.py
python3 component/retry-component/scripts/verify-comment-style.py

echo "[4/4] Run the full Maven reactor"
for command_name in java mvn; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is not available: $command_name" >&2
    exit 127
  fi
done
mvn -B -ntp -U clean verify
