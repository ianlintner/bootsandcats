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

MVNW="${REPO_ROOT}/mvnw"
if [[ ! -x "${MVNW}" ]]; then
  echo "Maven wrapper (${MVNW}) is missing or not executable." >&2
  exit 1
fi

# Ordered list of Maven goals that act as commit gates.
declare -a GOALS=(
  "spotless:check"     # formatting guard
  "spotbugs:check"     # static analysis / lint
  "verify"             # build + unit/integration tests
)

for goal in "${GOALS[@]}"; do
  printf '\n➡️  Running ./mvnw %s\n' "${goal}"
  "${MVNW}" ${goal}
done

printf '\n✅ All pre-commit checks passed.\n'
