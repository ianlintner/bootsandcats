#!/usr/bin/env bash
set -euo pipefail

# Summarize recent GitHub Actions runs for a repo using gh and jq.
# Usage:
#   scripts/gh-actions-summary.sh [owner/repo]
# If omitted, the script attempts to detect from the current git remote.

REPO_ARG="${1:-}"

# Resolve repo
if [[ -z "$REPO_ARG" ]]; then
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    REMOTE_URL=$(git config --get remote.origin.url || true)
    if [[ "$REMOTE_URL" =~ github.com[:/](.+/.+)\.git ]]; then
      REPO_ARG="${BASH_REMATCH[1]}"
    elif [[ "$REMOTE_URL" =~ github.com[:/](.+/.+) ]]; then
      REPO_ARG="${BASH_REMATCH[1]}"
    fi
  fi
fi

if [[ -z "$REPO_ARG" ]]; then
  echo "Repo not detected. Usage: scripts/gh-actions-summary.sh owner/repo" >&2
  exit 1
fi

# Check dependencies
if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI not found. Install from https://cli.github.com/" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq not found. On macOS: brew install jq" >&2
  exit 1
fi

# Fetch recent workflow runs (last 50)
JSON=$(gh api --paginate -H "Accept: application/vnd.github+json" \
  "/repos/${REPO_ARG}/actions/runs?per_page=50" | jq -s 'add')

if [[ -z "$JSON" || "$JSON" == "null" ]]; then
  echo "No runs found or API returned empty payload." >&2
  exit 0
fi

# Summaries
TOTAL=$(echo "$JSON" | jq '.total_count // 0')
if [[ "$TOTAL" -eq 0 ]]; then
  echo "No runs found."; exit 0
fi

# Per-workflow status breakdown
echo "Workflow status breakdown (last $TOTAL runs):"
echo "$JSON" | jq -r '
  (.workflow_runs // [])
  | group_by(.name)
  | map({
      name: (.[0].name),
      total: length,
      by_conclusion: (group_by(.conclusion) | map({(.[0].conclusion // "in_progress"): length}) | add),
      avg_duration_s: ((map((.run_started_at, .updated_at) | @sh) | length) as $l | (map((.run_started_at | fromdateiso8601) as $s | (.updated_at // .run_started_at | fromdateiso8601) - $s) | add) / ( if length == 0 then 1 else length end ))
    })
  | sort_by(.name)
  | .[]
  | "- " + .name + ": total=" + (.total|tostring) + ", conclusions=" + (.by_conclusion|tostring) + ", avg_dur_s=" + ((.avg_duration_s // 0)|floor|tostring)
'

# Slowest 5 runs
echo
echo "Slowest 5 runs:"
echo "$JSON" | jq -r '
  (.workflow_runs // [])
  | map({
      id: .id,
      name: .name,
      event: .event,
      head_branch: .head_branch,
      html_url: .html_url,
      duration_s: ((.run_started_at | fromdateiso8601) as $s | (.updated_at // .run_started_at | fromdateiso8601) - $s)
    })
  | sort_by(.duration_s) | reverse | .[:5]
  | .[] | "- [" + .name + "](" + .html_url + ") branch=" + .head_branch + ", event=" + .event + ", dur_s=" + ((.duration_s // 0)|floor|tostring)
'

# Cache hits (best-effort heuristic from job logs metadata)
echo
echo "Note: To assess cache hit rates precisely, inspect job logs for setup-java cache and actions/cache steps."

