#!/usr/bin/env bash
# Builds ElementCtrl's copy of Element Call (the in-app call screen) from source.
#
#   element-call/build.sh
#
# 1. Fetches Element Call at the commit pinned in upstream.env, and checks that the
#    release tag points at that same commit.
# 2. Applies patches/*.patch and copies overrides/ over it. Together they leave out
#    everything ElementCtrl never uses: Google's reCAPTCHA and the sign-up flow,
#    PostHog analytics, Sentry crash reporting, bug-report (rageshake) upload, and
#    background blur with Google's MediaPipe.
# 3. Installs the locked dependencies (pnpm install --frozen-lockfile) and runs the
#    embedded build, the one Element X ships, with vite-elementctrl.config.ts.
# 4. Checks the result and puts it in element-call/dist/element-call/, which
#    app/build.gradle.kts packs into the APK's assets as element-call/.
#
# Needs git, Node.js 24 and Corepack (for the pnpm version Element Call pins, which
# Corepack checks against its sha512). Element Call's code is AGPL-3.0: its licence
# and a note of the commit and patches go into the output next to it.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=upstream.env
. "$here/upstream.env"
work="${EC_WORK_DIR:-$here/.work}"
dist="$here/dist/element-call"

echo "== Element Call $EC_TAG ($EC_COMMIT)"

# The release tag must point at the pinned commit (an annotated tag's ^{} line wins).
tag_sha=$(git ls-remote "$EC_REPO" "refs/tags/$EC_TAG" "refs/tags/$EC_TAG^{}" | sort -k2 | awk 'END { print $1 }')
if [ "$tag_sha" != "$EC_COMMIT" ]; then
  echo "::error title=Element Call tag mismatch::$EC_TAG points at ${tag_sha:-nothing}, expected $EC_COMMIT" >&2
  exit 1
fi

rm -rf "$work"
mkdir -p "$work"
git -C "$work" init -q
git -C "$work" remote add origin "$EC_REPO"
GIT_LFS_SKIP_SMUDGE=1 git -C "$work" fetch -q --depth 1 --no-tags origin "$EC_COMMIT"
git -C "$work" -c advice.detachedHead=false checkout -q FETCH_HEAD
if [ "$(git -C "$work" rev-parse HEAD)" != "$EC_COMMIT" ]; then
  echo "::error title=Element Call checkout::Fetched $(git -C "$work" rev-parse HEAD), expected $EC_COMMIT" >&2
  exit 1
fi

echo "== Applying ElementCtrl's patches"
for p in "$here"/patches/*.patch; do
  echo "   $(basename "$p")"
  git -C "$work" apply --whitespace=nowarn "$p"
done
cp -R "$here/overrides/." "$work/"
# The sign-up page's reCAPTCHA strings (which name Google) are gone from every language.
node "$here/strip-locales.mjs" "$work/locales"

echo "== Installing dependencies"
cd "$work"
# @sentry/cli is a build tool of Element Call's (for uploading source maps); don't
# let its install script download its binary from Sentry, it's never used here.
export SENTRYCLI_SKIP_DOWNLOAD=1
export COREPACK_ENABLE_DOWNLOAD_PROMPT=0
pnpm install --frozen-lockfile --ignore-pnpmfile ${EC_PNPM_STORE:+--store-dir "$EC_PNPM_STORE"}

echo "== Building"
rm -rf dist
VITE_APP_VERSION="embedded-$EC_TAG-elementctrl" \
  node --max-old-space-size=6144 node_modules/vite/bin/vite.js build --config vite-elementctrl.config.ts

echo "== Checking the build"
test -f dist/index.html || { echo "::error title=Element Call build::dist/index.html is missing" >&2; exit 1; }
# None of what was removed may come back unnoticed after an update.
if grep -r -l -i -E 'recaptcha\.net|grecaptcha|google\.com/(policies|recaptcha)|policies\.google\.com|posthog\.com|sentry\.io|sentry-cdn|storage\.googleapis\.com|mediapipe|fonts\.(googleapis|gstatic)\.com|stun\.l\.google\.com' dist; then
  echo "::error title=Element Call build::Removed Google/analytics/crash-reporting code is back in the build (files above)" >&2
  exit 1
fi
find dist -name '*.map' -delete
# What's still named, for the record (protocol and type names that calls need; see README).
pat='google|gstatic|googleapis|recaptcha|mediapipe|posthog|sentry'
counts=$(grep -r -o -i -E "$pat" dist | awk -F: '{ n = $1; sub(/^dist\//, "", n); print n ": " tolower($2) }' | sort | uniq -c | sort -rn || true)
context=$(grep -r -o -h -i -E ".{0,50}($pat).{0,50}" dist | sort -u | head -n 80 || true)
annotate() { printf '%s' "$2" | sed 's/%/%25/g' | awk -v t="$1" 'BEGIN { printf "::notice title=%s::", t } { printf "%s%%0A", $0 } END { print "" }'; }
if [ -n "${GITHUB_ACTIONS:-}" ]; then
  annotate "Element Call: remaining mentions" "${counts:-none}"
  [ -n "$context" ] && annotate "Element Call: where" "$context"
else
  echo "Remaining mentions:"; echo "${counts:-  none}"
fi

rm -rf "$dist"
mkdir -p "$(dirname "$dist")"
cp -R dist "$dist"
cp LICENSE-AGPL-3.0 "$dist/LICENSE-AGPL-3.0"
{
  echo "Element Call, built from source for ElementCtrl."
  echo
  echo "Source:  $EC_REPO"
  echo "Release: $EC_TAG (commit $EC_COMMIT)"
  echo "Licence: AGPL-3.0-only (LICENSE-AGPL-3.0)"
  echo
  echo "ElementCtrl's changes are in element-call/ in ElementCtrl's repository"
  echo "(https://github.com/VoidedKN0X/ElementCtrl): build.sh, patches/ and overrides/."
  echo "Patches applied:"
  for p in "$here"/patches/*.patch; do echo "  $(basename "$p")"; done
  echo "Files replaced or added:"
  (cd "$here/overrides" && find . -type f | sed 's|^\./|  |' | sort)
} > "$dist/SOURCE.txt"

echo "== Element Call is in $dist ($(du -sh "$dist" | cut -f1), $(find "$dist" -type f | wc -l) files)"
