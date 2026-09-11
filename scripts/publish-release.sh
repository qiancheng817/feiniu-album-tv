#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

VERSION_NAME=""
VERSION_CODE=""
TAG=""
DEFAULT_RELEASE_REPO="qiancheng817/feiniu-album-tv"
LEGACY_RELEASE_REPO="qiancheng817/feiniu-album-tv"
RELEASE_REPO="$DEFAULT_RELEASE_REPO"
PUBLISH=0
CONFIGURE_PAGES=1
RUN_TESTS=1
SELF_TEST=0
PUBLIC_DIR="public-release"
DIST_DIR="dist/release"
APP_NAME="feiniu-album-tv"
PRODUCT_NAME="飞牛相册 TV"
PRIVATE_REPO=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/publish-release.sh [options]

Builds signed release APKs, updates public release docs, and optionally publishes
the public site plus APK assets to GitHub.

Options:
  --release-repo OWNER/REPO   Public GitHub repo. Default: qiancheng817/feiniu-album-tv.
  --private-repo OWNER/REPO   Optional private source repo to create if it does not exist.
  --version-name VERSION      App version name. Default: increment fnVersionName.
  --version-code CODE         Optional app version code. Default: increment fnVersionCode.
  --tag TAG                   GitHub Release tag. Default: v<VERSION>
  --publish                   Push public site and upload APKs to GitHub Release.
  --no-pages                  Do not configure GitHub Pages.
  --skip-tests                Build without running unit tests first.
  --self-test                 Run release script helper tests without building.
  -h, --help                  Show this help.

Examples:
  scripts/publish-release.sh --release-repo qiancheng817/feiniu-album-tv --publish
  scripts/publish-release.sh --release-repo qiancheng817/feiniu-album-tv --version-name 1.1 --tag v1.1 --publish
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-repo)
      RELEASE_REPO="${2:?Missing value for --release-repo}"
      shift 2
      ;;
    --private-repo)
      PRIVATE_REPO="${2:?Missing value for --private-repo}"
      shift 2
      ;;
    --version-name)
      VERSION_NAME="${2:?Missing value for --version-name}"
      shift 2
      ;;
    --version-code)
      VERSION_CODE="${2:?Missing value for --version-code}"
      shift 2
      ;;
    --tag)
      TAG="${2:?Missing value for --tag}"
      shift 2
      ;;
    --publish)
      PUBLISH=1
      shift
      ;;
    --no-pages)
      CONFIGURE_PAGES=0
      shift
      ;;
    --skip-tests)
      RUN_TESTS=0
      shift
      ;;
    --self-test)
      SELF_TEST=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

FILE_PREFIX=""
UNIVERSAL_APK=""
ARM32_APK=""
SHA_FILE=""
UPDATE_MANIFEST=""
RELEASE_URL=""
PAGES_URL=""

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

sdk_dir() {
  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
    printf '%s\n' "$ANDROID_HOME"
    return
  fi
  if [[ -f local.properties ]]; then
    awk -F= '$1 == "sdk.dir" {print substr($0, index($0, "=") + 1)}' local.properties
  fi
}

find_apksigner() {
  local sdk
  sdk="$(sdk_dir)"
  if [[ -z "$sdk" || ! -d "$sdk/build-tools" ]]; then
    echo "Cannot find Android SDK build-tools. Set ANDROID_HOME or sdk.dir in local.properties." >&2
    exit 1
  fi
  find "$sdk/build-tools" -type f -name apksigner | sort | tail -n 1
}

read_gradle_property() {
  local key="$1"
  awk -F= -v key="$key" '$1 == key {print substr($0, index($0, "=") + 1); exit}' gradle.properties
}

read_local_property() {
  local key="$1"
  local value=""
  if [[ -f local.properties ]]; then
    value="$(awk -F= -v key="$key" '$1 == key {print substr($0, index($0, "=") + 1); exit}' local.properties)"
  fi
  if [[ -z "$value" && -f private/signing/release-signing.properties ]]; then
    value="$(awk -F= -v key="$key" '$1 == key {print substr($0, index($0, "=") + 1); exit}' private/signing/release-signing.properties)"
  fi
  printf '%s\n' "$value"
}

release_signing_value() {
  local property_name="$1"
  local environment_name="$2"
  local environment_value="${!environment_name:-}"

  if [[ -n "$environment_value" ]]; then
    printf '%s\n' "$environment_value"
  else
    read_local_property "$property_name"
  fi
}

require_release_signing() {
  local store_file
  local store_password
  local key_alias
  local key_password

  store_file="$(release_signing_value fnphoto.release.storeFile FNPHOTO_RELEASE_STORE_FILE)"
  store_password="$(release_signing_value fnphoto.release.storePassword FNPHOTO_RELEASE_STORE_PASSWORD)"
  key_alias="$(release_signing_value fnphoto.release.keyAlias FNPHOTO_RELEASE_KEY_ALIAS)"
  key_password="$(release_signing_value fnphoto.release.keyPassword FNPHOTO_RELEASE_KEY_PASSWORD)"

  if [[ -z "$store_file" || -z "$store_password" || -z "$key_alias" || -z "$key_password" ]]; then
    echo "Release signing is not configured. Use ignored local.properties or FNPHOTO_RELEASE_* environment variables." >&2
    exit 1
  fi
  if [[ ! -f "$store_file" ]]; then
    echo "Configured release keystore does not exist." >&2
    exit 1
  fi
}

set_gradle_property() {
  local key="$1"
  local value="$2"
  local tmp
  tmp="$(mktemp)"
  if grep -q "^${key}=" gradle.properties; then
    awk -F= -v key="$key" -v value="$value" '
      $1 == key { print key "=" value; next }
      { print }
    ' gradle.properties > "$tmp"
  else
    cat gradle.properties > "$tmp"
    printf '%s=%s\n' "$key" "$value" >> "$tmp"
  fi
  mv "$tmp" gradle.properties
}

increment_version_name() {
  local version="${1#v}"
  local major minor patch
  IFS=. read -r major minor patch _ <<< "$version"
  major="${major:-1}"
  minor="${minor:-0}"
  patch="${patch:-0}"

  if ! [[ "$major" =~ ^[0-9]+$ && "$minor" =~ ^[0-9]+$ && "$patch" =~ ^[0-9]+$ ]]; then
    echo "Cannot auto-increment non-numeric version: $1" >&2
    return 1
  fi

  patch=$((patch + 1))
  printf '%s.%s.%s\n' "$major" "$minor" "$patch"
}

increment_version_code() {
  local code="${1:-0}"
  if ! [[ "$code" =~ ^[0-9]+$ ]]; then
    echo "Cannot auto-increment non-numeric versionCode: $1" >&2
    return 1
  fi
  printf '%s\n' "$((code + 1))"
}

normalize_release_repo() {
  local repo="${1:-$DEFAULT_RELEASE_REPO}"
  if [[ "$repo" == "$LEGACY_RELEASE_REPO" ]]; then
    printf '%s\n' "$DEFAULT_RELEASE_REPO"
    return
  fi
  printf '%s\n' "$repo"
}

prepare_release_metadata() {
  local current_version_name
  local current_version_code
  RELEASE_REPO="$(normalize_release_repo "$RELEASE_REPO")"
  current_version_name="$(read_gradle_property fnVersionName)"
  current_version_code="$(read_gradle_property fnVersionCode)"

  if [[ -z "$VERSION_NAME" ]]; then
    VERSION_NAME="$(increment_version_name "${current_version_name:-1.0}")"
  fi
  if [[ -z "$VERSION_CODE" ]]; then
    VERSION_CODE="$(increment_version_code "${current_version_code:-1}")"
  fi

  set_gradle_property fnVersionName "$VERSION_NAME"
  set_gradle_property fnVersionCode "$VERSION_CODE"

  TAG="${TAG:-v${VERSION_NAME}}"
  FILE_PREFIX="${APP_NAME}-v${VERSION_NAME}"
  UNIVERSAL_APK="${DIST_DIR}/${FILE_PREFIX}-universal.apk"
  ARM32_APK="${DIST_DIR}/${FILE_PREFIX}-armeabi-v7a.apk"
  SHA_FILE="${DIST_DIR}/SHA256SUMS.txt"
  UPDATE_MANIFEST="${DIST_DIR}/${APP_NAME}-update.json"

  if [[ -n "$RELEASE_REPO" ]]; then
    RELEASE_URL="https://github.com/${RELEASE_REPO}/releases/tag/${TAG}"
    owner="${RELEASE_REPO%%/*}"
    repo="${RELEASE_REPO#*/}"
    PAGES_URL="https://${owner}.github.io/${repo}/"
  fi
}

run_sensitive_scan() {
  echo "Scanning release files for sensitive information..."
  scripts/check-sensitive.sh
}

file_size_bytes() {
  local file="$1"
  stat -f%z "$file" 2>/dev/null || stat -c%s "$file"
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '%s' "$value"
}

generate_update_manifest() {
  local universal_name
  local arm32_name
  local title
  local notes
  universal_name="$(basename "$UNIVERSAL_APK")"
  arm32_name="$(basename "$ARM32_APK")"
  title="$(json_escape "${PRODUCT_NAME} ${TAG}")"
  notes="$(json_escape "下载后请通过系统安装器确认安装。")"

  cat > "$UPDATE_MANIFEST" <<JSON
{
  "version": "${TAG}",
  "title": "${title}",
  "page_url": "${RELEASE_URL}",
  "notes": "${notes}",
  "assets": [
    {
      "name": "${universal_name}",
      "download_url": "https://github.com/${RELEASE_REPO}/releases/download/${TAG}/${universal_name}",
      "size": $(file_size_bytes "$UNIVERSAL_APK")
    },
    {
      "name": "${arm32_name}",
      "download_url": "https://github.com/${RELEASE_REPO}/releases/download/${TAG}/${arm32_name}",
      "size": $(file_size_bytes "$ARM32_APK")
    }
  ]
}
JSON
}

replace_marked_block() {
  local file="$1"
  local start="$2"
  local end="$3"
  local replacement="$4"
  local tmp
  local replacement_file
  tmp="$(mktemp)"
  replacement_file="$(mktemp)"
  printf '%s\n' "$replacement" > "$replacement_file"
  awk -v start="$start" -v end="$end" -v replacement_file="$replacement_file" '
    $0 == start {
      print
      while ((getline line < replacement_file) > 0) {
        print line
      }
      close(replacement_file)
      skipping = 1
      next
    }
    $0 == end {
      skipping = 0
      print
      next
    }
    !skipping { print }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
  rm -f "$replacement_file"
}

replace_apk_names_in_file() {
  local file="$1"
  local tmp
  tmp="$(mktemp)"
  awk -v file_prefix="$FILE_PREFIX" '
    {
      gsub(/(feiniu-album-tv|feiniu-album-tv)-v[[:alnum:]._-]+-universal\.apk/, file_prefix "-universal.apk")
      gsub(/(feiniu-album-tv|feiniu-album-tv)-v[[:alnum:]._-]+-armeabi-v7a\.apk/, file_prefix "-armeabi-v7a.apk")
      print
    }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}

update_public_docs() {
  if [[ -n "$RELEASE_REPO" ]]; then
    local release_block
    release_block="- 产品介绍页：[${PAGES_URL}](${PAGES_URL})
- 下载页面：[${RELEASE_URL}](${RELEASE_URL})"

    replace_marked_block README.md "<!-- release-links:start -->" "<!-- release-links:end -->" "$release_block"
    replace_marked_block "${PUBLIC_DIR}/README.md" "<!-- release-links:start -->" "<!-- release-links:end -->" "$release_block"
  else
    echo "No --release-repo provided; keeping release links unchanged."
  fi

  replace_apk_names_in_file README.md
  replace_apk_names_in_file "${PUBLIC_DIR}/README.md"

  local tmp
  tmp="$(mktemp)"
  awk -v release_url="$RELEASE_URL" -v file_prefix="$FILE_PREFIX" '
    {
      if (release_url != "") {
        gsub(/href="\.\/releases\/(feiniu-album-tv|feiniu-album-tv)-v[^\"]+\.apk"/, "href=\"" release_url "\"")
        gsub(/href="https:\/\/github\.com\/[^"]+\/releases\/tag\/[^"]+"/, "href=\"" release_url "\"")
        gsub(/href="https:\/\/github\.com\/[^"]+\/releases\/latest"/, "href=\"" release_url "\"")
        gsub(/ download>/, ">")
      }
      gsub(/(feiniu-album-tv|feiniu-album-tv)-v[[:alnum:]._-]+-universal\.apk/, file_prefix "-universal.apk")
      gsub(/(feiniu-album-tv|feiniu-album-tv)-v[[:alnum:]._-]+-armeabi-v7a\.apk/, file_prefix "-armeabi-v7a.apk")
      print
    }
  ' "${PUBLIC_DIR}/index.html" > "$tmp"
  mv "$tmp" "${PUBLIC_DIR}/index.html"
}

assert_self_test_equals() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "Self-test failed: ${name}: expected '${expected}', got '${actual}'" >&2
    exit 1
  fi
}

run_self_tests() {
  assert_self_test_equals "increment short version" "1.0.1" "$(increment_version_name "1.0")"
  assert_self_test_equals "increment patch version" "1.2.4" "$(increment_version_name "1.2.3")"
  assert_self_test_equals "increment version code" "8" "$(increment_version_code "7")"
  assert_self_test_equals "default release repo" "qiancheng817/feiniu-album-tv" "$(normalize_release_repo "")"
  assert_self_test_equals "legacy release repo" "qiancheng817/feiniu-album-tv" "$(normalize_release_repo "qiancheng817/feiniu-album-tv")"
  assert_self_test_equals "canonical release repo" "qiancheng817/feiniu-album-tv" "$(normalize_release_repo "qiancheng817/feiniu-album-tv")"

  local tmp
  tmp="$(mktemp -d)"
  (
    cd "$tmp"
    printf '%s\n' "fnVersionName=1.2.3" "fnVersionCode=9" > gradle.properties
    VERSION_NAME=""
    VERSION_CODE=""
    TAG=""
    RELEASE_REPO="owner/repo"
    prepare_release_metadata
    assert_self_test_equals "prepare version name" "1.2.4" "$(read_gradle_property fnVersionName)"
    assert_self_test_equals "prepare version code" "10" "$(read_gradle_property fnVersionCode)"
    assert_self_test_equals "prepare tag" "v1.2.4" "$TAG"

    printf '%s\n' 'Download `feiniu-album-tv-v1.0-universal.apk` or feiniu-album-tv-v1.0-armeabi-v7a.apk' > names.md
    replace_apk_names_in_file names.md
    assert_self_test_equals "replace apk names" 'Download `feiniu-album-tv-v1.2.4-universal.apk` or feiniu-album-tv-v1.2.4-armeabi-v7a.apk' "$(cat names.md)"

    mkdir -p dist/release
    UNIVERSAL_APK="dist/release/feiniu-album-tv-v1.2.4-universal.apk"
    ARM32_APK="dist/release/feiniu-album-tv-v1.2.4-armeabi-v7a.apk"
    UPDATE_MANIFEST="dist/release/feiniu-album-tv-update.json"
    printf 'universal' > "$UNIVERSAL_APK"
    printf 'arm32' > "$ARM32_APK"
    generate_update_manifest
    grep -q '"version": "v1.2.4"' "$UPDATE_MANIFEST"
    grep -q '"download_url": "https://github.com/owner/repo/releases/download/v1.2.4/feiniu-album-tv-v1.2.4-universal.apk"' "$UPDATE_MANIFEST"
  )

  rm -rf "$tmp"
  scripts/check-sensitive.sh --self-test
  run_sensitive_scan
  echo "Self-tests passed."
}

build_apks() {
  require_cmd unzip
  require_cmd shasum

  if [[ ! -x ./gradlew ]]; then
    echo "Missing executable ./gradlew" >&2
    exit 1
  fi

  require_release_signing

  local apksigner
  apksigner="$(find_apksigner)"
  if [[ -z "$apksigner" ]]; then
    echo "Cannot find apksigner." >&2
    exit 1
  fi

  rm -rf "$DIST_DIR"
  mkdir -p "$DIST_DIR"
  rm -rf "${PUBLIC_DIR}/releases"

  local base_gradle_args=("-PfnVersionName=${VERSION_NAME}")
  if [[ -n "$VERSION_CODE" ]]; then
    base_gradle_args+=("-PfnVersionCode=${VERSION_CODE}")
  fi
  if [[ -n "$RELEASE_REPO" ]]; then
    base_gradle_args+=("-PfnUpdateRepo=${RELEASE_REPO}")
  fi

  if [[ "$RUN_TESTS" -eq 1 ]]; then
    ./gradlew clean testDebugUnitTest assembleRelease "${base_gradle_args[@]}"
  else
    ./gradlew clean assembleRelease "${base_gradle_args[@]}"
  fi
  cp app/build/outputs/apk/release/app-release.apk "$UNIVERSAL_APK"

  ./gradlew clean assembleRelease "${base_gradle_args[@]}" -PfnAbi=armeabi-v7a
  cp app/build/outputs/apk/release/app-release.apk "$ARM32_APK"

  "$apksigner" verify --verbose "$UNIVERSAL_APK" >/dev/null
  "$apksigner" verify --verbose "$ARM32_APK" >/dev/null
  generate_update_manifest

  local arm32_abis
  arm32_abis="$(unzip -Z1 "$ARM32_APK" | awk -F/ '/^lib\// {print $2}' | sort -u | tr '\n' ' ' | sed 's/[[:space:]]*$//')"
  if [[ "$arm32_abis" != "armeabi-v7a" ]]; then
    echo "Unexpected ABI list for $ARM32_APK: $arm32_abis" >&2
    exit 1
  fi

  (
    cd "$DIST_DIR"
    LC_ALL=C shasum -a 256 "$(basename "$UNIVERSAL_APK")" "$(basename "$ARM32_APK")" "$(basename "$UPDATE_MANIFEST")" > "$(basename "$SHA_FILE")"
  )
}

publish_public_repo() {
  require_cmd gh
  require_cmd git

  if [[ -z "$RELEASE_REPO" ]]; then
    echo "--release-repo OWNER/REPO is required with --publish." >&2
    exit 1
  fi

  gh auth status >/dev/null

  if [[ -n "$PRIVATE_REPO" ]] && ! gh repo view "$PRIVATE_REPO" >/dev/null 2>&1; then
    gh repo create "$PRIVATE_REPO" --private --source . --remote private-origin --disable-wiki --disable-issues
  fi

  if ! gh repo view "$RELEASE_REPO" >/dev/null 2>&1; then
    gh repo create "$RELEASE_REPO" --public --description "${PRODUCT_NAME} 发布页" --disable-wiki --disable-issues
  fi

  local tmp_repo
  tmp_repo="$(mktemp -d)"
  gh repo clone "$RELEASE_REPO" "$tmp_repo" -- --depth=1
  cp "${PUBLIC_DIR}/README.md" "$tmp_repo/README.md"
  cp "${PUBLIC_DIR}/index.html" "$tmp_repo/index.html"

  (
    cd "$tmp_repo"
    git add README.md index.html
    if ! git diff --cached --quiet; then
      git -c user.name="feiniu-release-bot" \
        -c user.email="release-bot@users.noreply.github.com" \
        commit -m "Update public release page" >/dev/null
      git push origin HEAD:main
    fi
  )

  if [[ "$CONFIGURE_PAGES" -eq 1 ]]; then
    if gh api "repos/${RELEASE_REPO}/pages" >/dev/null 2>&1; then
      gh api -X PUT "repos/${RELEASE_REPO}/pages" --input - >/dev/null <<JSON
{"source":{"branch":"main","path":"/"}}
JSON
    else
      gh api -X POST "repos/${RELEASE_REPO}/pages" --input - >/dev/null <<JSON
{"source":{"branch":"main","path":"/"}}
JSON
    fi
  fi

  local notes
  notes="$(mktemp)"
  cat > "$notes" <<NOTES
${PRODUCT_NAME} ${TAG}

下载建议：
- ${FILE_PREFIX}-universal.apk：通用包，适合不确定设备 ABI 或希望兼容更多电视盒子的用户。
- ${FILE_PREFIX}-armeabi-v7a.apk：32 位 ARM 单 ABI 包，适合 armeabi-v7a 设备，体积更小。

校验：
\`\`\`
$(cat "$SHA_FILE")
\`\`\`
NOTES

  if gh release view "$TAG" --repo "$RELEASE_REPO" >/dev/null 2>&1; then
    while IFS= read -r asset_id; do
      if [[ -n "$asset_id" ]]; then
        gh api -X DELETE "repos/${RELEASE_REPO}/releases/assets/${asset_id}" >/dev/null
      fi
    done < <(gh api "repos/${RELEASE_REPO}/releases/tags/${TAG}" --jq '.assets[].id')
    gh release upload "$TAG" "$UNIVERSAL_APK" "$ARM32_APK" "$UPDATE_MANIFEST" "$SHA_FILE" --repo "$RELEASE_REPO"
    gh release edit "$TAG" --repo "$RELEASE_REPO" --title "${PRODUCT_NAME} ${TAG}" --notes-file "$notes"
  else
    gh release create "$TAG" "$UNIVERSAL_APK" "$ARM32_APK" "$UPDATE_MANIFEST" "$SHA_FILE" \
      --repo "$RELEASE_REPO" \
      --title "${PRODUCT_NAME} ${TAG}" \
      --notes-file "$notes"
  fi
}

main() {
  if [[ "$SELF_TEST" -eq 1 ]]; then
    run_self_tests
    return
  fi

  prepare_release_metadata
  run_sensitive_scan
  build_apks
  update_public_docs

  if [[ "$PUBLISH" -eq 1 ]]; then
    publish_public_repo
  fi

  echo "Release artifacts:"
  ls -lh "$UNIVERSAL_APK" "$ARM32_APK" "$UPDATE_MANIFEST" "$SHA_FILE"
  if [[ -n "$RELEASE_URL" ]]; then
    echo "Release URL: $RELEASE_URL"
    echo "Pages URL:   $PAGES_URL"
  fi
}

main "$@"
