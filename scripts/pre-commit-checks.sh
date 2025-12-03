#!/usr/bin/env bash
set -euo pipefail

if ! command -v git >/dev/null 2>&1; then
  echo "git is required to run the pre-commit checks. Aborting." >&2
  exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
if [[ -z "${REPO_ROOT}" ]]; then
  echo "Unable to determine repository root. Are you inside the repository?" >&2
  exit 1
fi

cd "${REPO_ROOT}"

GRADLEW="${REPO_ROOT}/gradlew"
if [[ ! -x "${GRADLEW}" ]]; then
  echo "Gradle wrapper (${GRADLEW}) is missing or not executable." >&2
  exit 1
fi

# Ordered list of Gradle tasks that act as commit gates.
declare -a TASKS=(
  "spotlessCheck"      # formatting guard
  "spotbugsMain"       # static analysis / lint
  "build"              # build + unit/integration tests
)

for task in "${TASKS[@]}"; do
  printf '\n➡️  Running ./gradlew %s\n' "${task}"
  "${GRADLEW}" ${task}
done

printf '\n✅ All pre-commit checks passed.\n'
