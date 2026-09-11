#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

found=0

report_path() {
  printf '%s: tracked-sensitive-file\n' "$1" >&2
  found=1
}

sensitive_path_reason() {
  case "$1" in
    private/*|*/private/*|*.jks|*.keystore|*.p12|*.pfx|*.key)
      return 0
      ;;
    .env|.env.*|*/.env|*/.env.*|*credentials.json|*release-signing.properties)
      return 0
      ;;
    *.pem)
      [[ "$1" != "app/src/main/res/raw/cacert.pem" ]]
      return
      ;;
  esac
  return 1
}

scan_tracked_paths() {
  local path
  while IFS= read -r path; do
    if sensitive_path_reason "$path"; then
      report_path "$path"
    fi
  done < <(
    {
      git ls-files
      git ls-files --others --exclude-standard
    } | sort -u
  )
}

scan_text() {
  local reason="$1"
  local pattern="$2"
  local grep_status

  git grep --untracked --exclude-standard -I -n -E -e "$pattern" -- . \
    | awk -F: -v reason="$reason" '{print $1 ":" $2 ": " reason > "/dev/stderr"}'
  grep_status=${PIPESTATUS[0]}

  if [[ "$grep_status" -eq 0 ]]; then
    found=1
  elif [[ "$grep_status" -ne 1 ]]; then
    printf 'sensitive scan failed while checking %s\n' "$reason" >&2
    return 2
  fi
}

run_scan() {
  local user_path_pattern
  user_path_pattern='(/Us''ers/[^/[:space:]]+|[A-Za-z]:\\Us''ers\\[^\\[:space:]]+)'

  scan_tracked_paths
  scan_text "private-key-block" '-----BEGIN ([A-Z0-9]+ )?PRIVATE KEY-----'
  scan_text "well-known-service-token" '(AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|github_pat_[A-Za-z0-9_]{40,}|gh[pousr]_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{30,}|xox[baprs]-[0-9A-Za-z-]{10,}|sk-(live|test)-[0-9A-Za-z]{16,})'
  scan_text "hard-coded-credential" "[\"']?(password|passwd|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|storePassword|keyPassword)[[:alnum:]_.-]*[\"']?[[:space:]]*[:=][[:space:]]*[\"'][^\"'[:space:]\$<{][^\"']{15,}[\"']"
  scan_text "local-user-path" "$user_path_pattern"

  if [[ -n "${FNPHOTO_SENSITIVE_PATTERN:-}" ]]; then
    scan_text "project-private-marker" "$FNPHOTO_SENSITIVE_PATTERN"
  fi
}

run_self_test() {
  local service_sample
  local private_sample
  service_sample="ghp_""123456789012345678901234567890123456"
  private_sample="-----BEGIN ""PRIVATE KEY-----"

  printf '%s\n' "$service_sample" | grep -Eq 'gh[pousr]_[A-Za-z0-9_]{20,}' \
    || { echo "self-test failed: service token pattern" >&2; return 1; }
  printf '%s\n' "$private_sample" | grep -E -q -e '-----BEGIN ([A-Z0-9]+ )?PRIVATE KEY-----' \
    || { echo "self-test failed: private key pattern" >&2; return 1; }
  sensitive_path_reason "private/signing/release.jks" \
    || { echo "self-test failed: sensitive path" >&2; return 1; }
  if sensitive_path_reason "app/src/main/res/raw/cacert.pem"; then
    echo "self-test failed: public CA bundle allowlist" >&2
    return 1
  fi
}

if [[ "${1:-}" == "--self-test" ]]; then
  run_self_test
  echo "Sensitive scan self-tests passed."
  exit 0
fi

run_scan
if [[ "$found" -ne 0 ]]; then
  echo "Sensitive information detected; aborting." >&2
  exit 1
fi

echo "Sensitive information scan passed."
