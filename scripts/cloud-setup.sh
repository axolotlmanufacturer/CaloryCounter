#!/usr/bin/env bash
# Prepares a Linux container (the Claude Code cloud environment) to build Kalorienzähler:
#   - Android SDK command-line tools (latest, checksum-verified), platform 37, build-tools,
#     platform-tools, licenses accepted
#   - ANDROID_HOME/PATH persisted for later shells, local.properties when run from the repo
#   - a Gradle init script that prefers repo1.maven.org (repo.maven.apache.org answers 429
#     through the container's egress proxy)
#
# Idempotent. Needs network access to dl.google.com.
# Usage:  bash scripts/cloud-setup.sh
# It can also be pasted into the cloud environment's "Setup script" so that every new
# session starts with the SDK installed (it does not depend on the repository checkout).
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
REPO_URL="https://dl.google.com/android/repository"
SDK_PACKAGES=("platform-tools" "platforms;android-37" "build-tools;36.0.0")

log() { printf '[cloud-setup] %s\n' "$*"; }

install_cmdline_tools() {
  if [[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    log "command-line tools already installed"
    return
  fi
  local tmp zip sha1
  tmp="$(mktemp -d)"
  log "looking up the latest command-line tools"
  curl -fsSL "$REPO_URL/repository2-3.xml" -o "$tmp/repository.xml"
  read -r zip sha1 < <(python3 -I - "$tmp/repository.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

def local(tag):
    return tag.rsplit("}", 1)[-1]

def child(el, name):
    return next((c for c in el if local(c.tag) == name), None)

root = ET.parse(sys.argv[1]).getroot()
for pkg in root.iter():
    if local(pkg.tag) != "remotePackage" or pkg.get("path") != "cmdline-tools;latest":
        continue
    for archive in (el for el in pkg.iter() if local(el.tag) == "archive"):
        host = child(archive, "host-os")
        if host is not None and host.text == "linux":
            complete = child(archive, "complete")
            print(child(complete, "url").text, child(complete, "checksum").text)
            sys.exit(0)
sys.exit("cmdline-tools;latest for linux not found in repository2-3.xml")
PY
  )
  if [[ -z "${zip:-}" || -z "${sha1:-}" ]]; then
    log "could not determine the command-line tools download"
    exit 1
  fi
  log "downloading $zip"
  curl -fsSL "$REPO_URL/$zip" -o "$tmp/$zip"
  echo "$sha1  $tmp/$zip" | sha1sum -c --quiet -
  unzip -q "$tmp/$zip" -d "$tmp/unpacked"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/unpacked/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
}

install_sdk_packages() {
  local sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
  log "accepting SDK licenses"
  (yes || true) | "$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
  log "installing ${SDK_PACKAGES[*]}"
  "$sdkmanager" --sdk_root="$ANDROID_HOME" --install "${SDK_PACKAGES[@]}" >/dev/null
}

persist_environment() {
  local export_home="export ANDROID_HOME=\"$ANDROID_HOME\""
  local export_path='export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"'
  local rc
  for rc in "$HOME/.bashrc" "$HOME/.profile"; do
    touch "$rc"
    grep -qxF "$export_home" "$rc" || printf '%s\n%s\n' "$export_home" "$export_path" >>"$rc"
  done
  # When run from the repository, also point Gradle at the SDK directly.
  local repo_root
  repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." 2>/dev/null && pwd || true)"
  if [[ -n "$repo_root" && -f "$repo_root/settings.gradle.kts" && ! -f "$repo_root/local.properties" ]]; then
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" >"$repo_root/local.properties"
    log "wrote $repo_root/local.properties"
  fi
}

install_gradle_init_script() {
  mkdir -p "$HOME/.gradle/init.d"
  # Same content as scripts/gradle/maven-central-repo1.init.gradle.kts (kept inline so the
  # script also works when pasted into the environment settings without the repo).
  cat >"$HOME/.gradle/init.d/maven-central-repo1.init.gradle.kts" <<'KTS'
// Cloud-container workaround (installed by scripts/cloud-setup.sh into ~/.gradle/init.d/).
// Through the container's egress proxy, repo.maven.apache.org often answers 429 Too Many
// Requests, while repo1.maven.org (the same Maven Central) does not. This adds repo1 in
// front of the project's repositories; Google's groups are excluded so they go to google().
// Not needed on a normal developer machine.
val repo1 = "https://repo1.maven.org/maven2/"

fun org.gradle.api.artifacts.dsl.RepositoryHandler.addRepo1() {
    maven(repo1) {
        name = "MavenCentralRepo1"
        content {
            excludeGroupByRegex("androidx\\..*")
            excludeGroupByRegex("com\\.android\\..*")
            excludeGroupByRegex("com\\.google\\.android\\..*")
        }
    }
}

beforeSettings {
    pluginManagement.repositories.addRepo1()
    dependencyResolutionManagement.repositories.addRepo1()
}
KTS
  log "installed Gradle init script (repo1.maven.org)"
}

install_cmdline_tools
install_sdk_packages
persist_environment
install_gradle_init_script
log "done. ANDROID_HOME=$ANDROID_HOME"
