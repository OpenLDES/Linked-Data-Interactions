#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd -- "${script_dir}/.." && pwd)"
invocation_dir="$(pwd -P)"
report_dir="${repository_root}/target/ldes-conformance-report"
tests_path="tests"
fail_on_non_pass=true
suite_ref="${LDES_CONFORMANCE_SUITE_REF:-main}"
work_dir=""
runner_pid=""

usage() {
  cat <<'EOF'
Run the latest LDES client conformance suite against this checkout.

Usage:
  scripts/run-ldes-conformance.sh [options]

Options:
  --fail-on-non-pass  Return a non-zero status when an applicable test does not pass
                      (default).
  --no-fail           Report conformance gaps without making the command fail.
  --report-dir PATH   Write JSON, EARL, and Markdown evidence to PATH.
  --tests PATH        Run a suite test directory (default: tests).
  --suite-ref REF     Run the conformance suite at REF (default: main).
  -h, --help          Show this help.

By default, any applicable non-passing test makes the command fail.
The suite is fetched from main unless --suite-ref or
LDES_CONFORMANCE_SUITE_REF is set. Use a commit SHA to reproduce a previous run.
EOF
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 2
}

cleanup() {
  if [[ -n "${runner_pid}" ]] && kill -0 "${runner_pid}" 2>/dev/null; then
    kill "${runner_pid}" 2>/dev/null || true
    wait "${runner_pid}" 2>/dev/null || true
  fi
  if [[ -n "${work_dir}" && -d "${work_dir}" && "${work_dir}" == /tmp/openldes-conformance.* ]]; then
    rm -rf -- "${work_dir}"
  fi
}

while (($#)); do
  case "$1" in
    --fail-on-non-pass)
      fail_on_non_pass=true
      shift
      ;;
    --no-fail)
      fail_on_non_pass=false
      shift
      ;;
    --report-dir)
      (($# >= 2)) || die "--report-dir requires a path"
      report_dir="$2"
      shift 2
      ;;
    --tests)
      (($# >= 2)) || die "--tests requires a path"
      tests_path="$2"
      shift 2
      ;;
    --suite-ref)
      (($# >= 2)) || die "--suite-ref requires a ref"
      suite_ref="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown option: $1"
      ;;
  esac
done

if [[ "${report_dir}" != /* ]]; then
  report_dir="${invocation_dir}/${report_dir}"
fi

for command_name in git java mvn node npm; do
  command -v "${command_name}" >/dev/null 2>&1 ||
    die "required command not found: ${command_name}"
done

node_major="$(node --version | sed -E 's/^v([0-9]+).*/\1/')"
[[ "${node_major}" =~ ^[0-9]+$ && "${node_major}" -ge 22 ]] ||
  die "Node.js 22 or newer is required"

java_major="$(
  java -version 2>&1 |
    sed -nE '1s/.*version "([0-9]+).*/\1/p'
)"
[[ "${java_major}" =~ ^[0-9]+$ && "${java_major}" -ge 21 ]] ||
  die "Java 21 or newer is required"

case "${report_dir}" in
  ""|"/"|"${repository_root}")
    die "unsafe report directory: ${report_dir}"
    ;;
esac

work_dir="$(mktemp -d /tmp/openldes-conformance.XXXXXX)"
trap cleanup EXIT
suite_dir="${work_dir}/suite"
maven_repository="${LDES_CONFORMANCE_MAVEN_REPO:-${HOME}/.m2/repository}"

printf 'Fetching the conformance suite at %s...\n' "${suite_ref}"
git init --quiet "${suite_dir}"
git -C "${suite_dir}" remote add origin \
  https://github.com/pietercolpaert/ldes-client-conformance-test-suite.git
git -C "${suite_dir}" fetch --quiet --depth 1 origin "${suite_ref}"
git -C "${suite_dir}" checkout --quiet FETCH_HEAD
suite_revision="$(git -C "${suite_dir}" rev-parse HEAD)"
printf 'Conformance suite revision: %s\n' "${suite_revision}"

(
  cd "${suite_dir}"
  npm ci
  npm run build
  node dist/cli.js validate --tests "${tests_path}"
)

# Keep the adapter with the client API it exercises. The test harness and
# scenarios still come from the requested upstream suite revision.
cp -- "${script_dir}/ldes-conformance/OpenLdesAdapter.java" \
  "${suite_dir}/adapters/openldes-ldi/src/main/java/org/openldes/conformance/OpenLdesAdapter.java"
cp -- "${script_dir}/ldes-conformance/adapter.mjs" \
  "${suite_dir}/adapters/openldes-ldi/adapter.mjs"
cp -- "${script_dir}/ldes-conformance/adapter.json" \
  "${suite_dir}/adapters/openldes-ldi/adapter.json"

client_revision="$(git -C "${repository_root}" rev-parse HEAD 2>/dev/null || printf 'workspace')"
if [[ -n "$(git -C "${repository_root}" status --porcelain 2>/dev/null)" ]]; then
  client_revision="${client_revision}-dirty"
fi

printf 'Building the OpenLDES client and conformance adapter...\n'
(
  cd "${suite_dir}"
  LDES_CT_OPENLDES_ROOT="${repository_root}" \
    LDES_CT_MAVEN_REPO="${maven_repository}" \
    adapters/openldes-ldi/build.sh
)

mkdir -p -- "${report_dir}"

run_arguments=(
  node dist/cli.js run
  --adapter openldes-ldi
  --tests "${tests_path}"
  --out "${report_dir}"
)
if [[ "${fail_on_non_pass}" == false ]]; then
  run_arguments+=(--no-fail)
fi

test_count="$(
  find "${suite_dir}/${tests_path}" -type f -name scenario.yaml 2>/dev/null |
    wc -l |
    tr -d ' '
)"
printf '\nRunning %s conformance scenarios...\n' "${test_count}"
printf 'The suite prints detailed results after all scenarios finish.\n'

set +e
(
  cd "${suite_dir}"
  LDES_CT_CLIENT_REVISION="${client_revision}" \
    LDES_CT_OPENLDES_ROOT="${repository_root}" \
    "${run_arguments[@]}"
) &
runner_pid=$!

elapsed_seconds=0
completed_count=0
while kill -0 "${runner_pid}" 2>/dev/null; do
  sleep 5
  elapsed_seconds=$((elapsed_seconds + 5))
  current_count="$(
    find "${report_dir}/evidence" -type f -name '*.json' 2>/dev/null |
      wc -l |
      tr -d ' '
  )"
  if ((current_count > completed_count)); then
    completed_count="${current_count}"
    printf 'Progress: %s/%s scenarios completed (%ss elapsed).\n' \
      "${completed_count}" "${test_count}" "${elapsed_seconds}"
  elif ((elapsed_seconds % 15 == 0)); then
    printf 'Still running: %s/%s scenarios completed (%ss elapsed).\n' \
      "${completed_count}" "${test_count}" "${elapsed_seconds}"
  fi
done

wait "${runner_pid}"
status=$?
runner_pid=""
set -e

# The harness recreates its output directory when starting a run.
mkdir -p -- "${report_dir}"
printf '%s\n' "${suite_revision}" > "${report_dir}/suite-revision.txt"

printf '\nConformance evidence: %s\n' "${report_dir}"
exit "${status}"
