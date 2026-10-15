#!/bin/bash
# SessionStart hook for Claude Code cloud sessions: installs the Android SDK, the Robolectric
# SDK jar and the Maven Central mirror init script (scripts/cloud-setup.sh), so that
# `./gradlew lint test assembleDebug` works right away. Does nothing on local machines.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

log_file="${TMPDIR:-/tmp}/kalorienzaehler-cloud-setup.log"
if ! bash scripts/cloud-setup.sh >"$log_file" 2>&1; then
  echo "scripts/cloud-setup.sh failed; last lines of $log_file:" >&2
  tail -n 30 "$log_file" >&2
  exit 1
fi

android_home="${ANDROID_HOME:-$HOME/android-sdk}"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"$android_home\""
    echo "export PATH=\"$android_home/cmdline-tools/latest/bin:$android_home/platform-tools:\$PATH\""
  } >>"$CLAUDE_ENV_FILE"
fi

echo "Android SDK ready at $android_home (setup log: $log_file)."
