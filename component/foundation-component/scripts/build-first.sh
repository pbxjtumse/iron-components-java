#!/usr/bin/env bash
set -euo pipefail

COMPONENT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$COMPONENT_ROOT"

python3 scripts/validate-poms.py
mvn -B -ntp -U clean verify
