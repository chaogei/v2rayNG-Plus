#!/usr/bin/env bash
# Offline checks for compute-version.sh. Run locally or in CI:
#   bash .github/scripts/compute-version-test.sh
# No git repo or network needed: the tag list is injected via TAG_LIST.

set -euo pipefail
cd "$(dirname "$0")"

FAILURES=0

# run <event> <tags> <input_tag> <sha> -> captured key=value output
run() {
  EVENT_NAME="$1" TAG_LIST="$2" INPUT_RELEASE_TAG="$3" COMMIT_SHA="$4" \
    GITHUB_OUTPUT="" bash compute-version.sh 2>/dev/null
}

expect() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "ok   $label"
  else
    echo "FAIL $label: expected [$expected], got [$actual]"
    FAILURES=$((FAILURES + 1))
  fi
}

TAGS_3=$'v2.3.5.1-plus\nv2.3.5.2-plus\nv2.3.5.3-plus\nv2.3.5-plus\nv2.4.0-unrelated'

# 1. Master push with v2.3.5.3-plus released -> v2.3.5.4-plus / 750,
#    regardless of how many PR builds ran in between (PRs never tag).
expect "release after .3" \
  $'version_code=750\nversion_name=2.3.5.4-plus\nrelease_tag=v2.3.5.4-plus' \
  "$(run push "$TAGS_3" "" deadbeef)"

# 2. PR build with the same tags: reuses N=3, marks itself, publishes no tag.
expect "PR after .3" \
  $'version_code=749\nversion_name=2.3.5.3-pr.abc1234-plus\nrelease_tag=' \
  "$(run pull_request "$TAGS_3" "" abc1234def5678)"

# 3. Fresh repo, no release tags yet.
expect "first release" \
  $'version_code=747\nversion_name=2.3.5.1-plus\nrelease_tag=v2.3.5.1-plus' \
  "$(run push "" "" deadbeef)"
expect "PR before first release" \
  $'version_code=746\nversion_name=2.3.5.0-pr.abc1234-plus\nrelease_tag=' \
  "$(run pull_request "" "" abc1234def5678)"

# 4. Manual workflow_dispatch with an explicit tag overrides name and tag.
expect "manual tag override" \
  $'version_code=750\nversion_name=2.4.0-plus\nrelease_tag=v2.4.0-plus' \
  "$(run workflow_dispatch "$TAGS_3" "v2.4.0-plus" deadbeef)"

# 5. Numeric (not lexicographic) ordering of the tag scan: 10 > 9.
TAGS_10=$'v2.3.5.9-plus\nv2.3.5.10-plus'
expect "numeric tag sort" \
  $'version_code=757\nversion_name=2.3.5.11-plus\nrelease_tag=v2.3.5.11-plus' \
  "$(run push "$TAGS_10" "" deadbeef)"

# 6. The updater (VersionUtil) compares only the numeric core before '-'/'+'.
#    A PR versionName must therefore start with "<BASE>.<LAST_N>-" so it can
#    never read newer than the released version.
PR_NAME=$(run pull_request "$TAGS_3" "" abc1234def5678 | sed -n 's/^version_name=//p')
case "$PR_NAME" in
  2.3.5.3-*) echo "ok   PR numeric core equals last release" ;;
  *)
    echo "FAIL PR numeric core: got [$PR_NAME]"
    FAILURES=$((FAILURES + 1))
    ;;
esac

if [ "$FAILURES" -gt 0 ]; then
  echo "$FAILURES check(s) failed" >&2
  exit 1
fi
echo "all checks passed"
