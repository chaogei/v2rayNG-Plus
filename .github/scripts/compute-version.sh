#!/usr/bin/env bash
# Compute versionCode / versionName / release tag for CI builds.
#
# Release versions never depend on github.run_number. PR runs consume a
# run_number too, so anchoring releases on it would leave holes: with
# v2.3.5.5-plus released and six PR builds in between, the next master push
# would have jumped to 2.3.5.12-plus. Instead:
#
#   Release (push to master / workflow_dispatch without an explicit tag):
#     N           = highest N among existing v<BASE>.N-plus tags, plus 1
#                   (no tags at all -> N = 1)
#     versionCode = 746 + N        (746 = committed baseline)
#     versionName = <BASE>.<N>-plus, release tag = v<versionName>
#     Example: with v2.3.5.3-plus released, the next master push is always
#     v2.3.5.4-plus / versionCode 750, no matter how many PR builds ran
#     in between. A re-run of an already released run re-reads the tags and
#     moves on to N+1 instead of colliding with its own tag.
#
#   PR build (no release is published):
#     versionCode = 746 + LAST_N   (reuses the last released code, never
#                                   consumes the next release N)
#     versionName = <BASE>.<LAST_N>-pr.<short sha>-plus
#     The in-app updater (VersionUtil) drops everything after '-'/'+', so a
#     PR build reads as exactly the last released version - never newer.
#     Someone who sideloads a PR artifact still gets offered the next real
#     release.
#
#   workflow_dispatch with an explicit release_tag input overrides the tag
#   and versionName; versionCode is still 746 + N. The tag is normalised to
#   start with "v" so it lands in the same namespace the scan reads.
#
#   Such a tag does not match v<BASE>.N-plus, so it cannot advance N on its
#   own. Every off-sequence "-plus" tag is therefore counted as one consumed
#   release slot (OFFSET below): without it, releasing v2.4.0-plus by hand and
#   then pushing to master would hand two different releases the same
#   versionCode, and Android refuses to upgrade across an equal code.
#   Note that only the code is kept monotonic: the versionName still restarts
#   from <BASE>, so after a manual release outside the <BASE> line, bump
#   BASE_VERSION_NAME here instead of relying on further manual tags.
#
# Inputs (environment):
#   EVENT_NAME        github.event_name ("pull_request" selects the PR path)
#   INPUT_RELEASE_TAG optional explicit tag from workflow_dispatch
#   COMMIT_SHA        commit sha, used in the PR version suffix
#   TAG_LIST          newline-separated tag list override (tests only);
#                     unset -> read from `git tag --list`
#
# Outputs: version_code / version_name / release_tag key=value lines,
# appended to $GITHUB_OUTPUT when set, printed to stdout otherwise.

set -euo pipefail

BASE_VERSION_CODE=746
BASE_VERSION_NAME="2.3.5"

BASE_ESCAPED=${BASE_VERSION_NAME//./\\.}

# Every release tag ends in -plus, so the scan has to see all of them, not just
# the v<BASE>.N-plus sequence: the off-sequence ones still consumed a code.
if [ -z "${TAG_LIST+x}" ]; then
  TAG_LIST=$(git tag --list "v*-plus")
fi

PLUS_TAGS=$(printf '%s\n' "$TAG_LIST" | grep -E '^v.+-plus$' || true)

LAST_N=$(printf '%s\n' "$PLUS_TAGS" \
  | sed -nE "s/^v${BASE_ESCAPED}\.([0-9]+)-plus$/\1/p" \
  | sort -n | tail -1)
LAST_N=${LAST_N:-0}

# v<BASE>-plus is the committed baseline and owns BASE_VERSION_CODE itself, so
# it is not an extra slot. Anything else outside the sequence is one.
TOTAL_PLUS=$(printf '%s\n' "$PLUS_TAGS" | grep -c . || true)
SEQUENCE_PLUS=$(printf '%s\n' "$PLUS_TAGS" | grep -cE "^v${BASE_ESCAPED}\.[0-9]+-plus$" || true)
BASELINE_PLUS=$(printf '%s\n' "$PLUS_TAGS" | grep -cE "^v${BASE_ESCAPED}-plus$" || true)
OFFSET=$((TOTAL_PLUS - SEQUENCE_PLUS - BASELINE_PLUS))

INPUT_TAG=$(printf '%s' "${INPUT_RELEASE_TAG:-}" | tr -d '[:space:]')

if [ "${EVENT_NAME:-}" = "pull_request" ]; then
  SHORT_SHA=$(printf '%s' "${COMMIT_SHA:-unknown}" | cut -c1-7)
  VERSION_CODE=$((BASE_VERSION_CODE + LAST_N + OFFSET))
  VERSION_NAME="${BASE_VERSION_NAME}.${LAST_N}-pr.${SHORT_SHA}-plus"
  RELEASE_TAG=""
else
  N=$((LAST_N + 1))
  VERSION_CODE=$((BASE_VERSION_CODE + N + OFFSET))
  if [ -n "$INPUT_TAG" ]; then
    case "$INPUT_TAG" in
      v*) RELEASE_TAG="$INPUT_TAG" ;;
      *) RELEASE_TAG="v${INPUT_TAG}" ;;
    esac
    VERSION_NAME="${RELEASE_TAG#v}"
  else
    VERSION_NAME="${BASE_VERSION_NAME}.${N}-plus"
    RELEASE_TAG="v${VERSION_NAME}"
  fi
fi

emit() {
  echo "version_code=$VERSION_CODE"
  echo "version_name=$VERSION_NAME"
  echo "release_tag=$RELEASE_TAG"
}
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  emit >> "$GITHUB_OUTPUT"
else
  emit
fi
echo "Computed versionName=$VERSION_NAME versionCode=$VERSION_CODE tag=${RELEASE_TAG:-<none: PR build>}" >&2
