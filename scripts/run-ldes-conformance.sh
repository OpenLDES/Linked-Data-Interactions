#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd -- "${script_dir}/.." && pwd)"
invocation_dir="$(pwd -P)"
report_dir="${repository_root}/target/ldes-conformance-report"
tests_path="tests"
fail_on_non_pass=false
work_dir=""
runner_pid=""

usage() {
  cat <<'EOF'
Run the latest LDES client conformance suite against this checkout.

Usage:
  scripts/run-ldes-conformance.sh [options]

Options:
  --fail-on-non-pass  Return a non-zero status when an applicable test does not pass.
  --report-dir PATH   Write JSON, EARL, and Markdown evidence to PATH.
  --tests PATH        Run a suite test directory (default: tests).
  -h, --help          Show this help.

By default, conformance gaps are reported without making the command fail.
The suite is cloned from its main branch on every invocation.
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

printf 'Fetching the latest conformance suite from main...\n'
git clone --quiet --depth 1 --branch main \
  https://github.com/pietercolpaert/ldes-client-conformance-test-suite.git \
  "${suite_dir}"

(
  cd "${suite_dir}"
  npm ci
  npm run build
  node dist/cli.js validate --tests "${tests_path}"
)

# These compatibility edits can be removed once the suite's OpenLDES adapter
# consumes the dataset API and obtains the tested revision from the environment.
adapter_module="${suite_dir}/adapters/openldes-ldi/adapter.mjs"
adapter_java="${suite_dir}/adapters/openldes-ldi/src/main/java/org/openldes/conformance/OpenLdesAdapter.java"

sed -i.bak \
  's/clientRevision: "[0-9a-f]*"/clientRevision: process.env.LDES_CT_CLIENT_REVISION ?? "unknown"/' \
  "${adapter_module}"
rm -f -- "${adapter_module}.bak"

sed -i.bak \
  's/RDFDataMgr.write(dataset, member.getModel(), Lang.NTRIPLES);/RDFDataMgr.write(dataset, member.getDataset(), Lang.NQUADS);/' \
  "${adapter_java}"
rm -f -- "${adapter_java}.bak"

grep -q 'member.getDataset(), Lang.NQUADS' "${adapter_java}" ||
  die "the latest suite adapter is incompatible with the OpenLDES dataset API"

ADAPTER_JAVA="${adapter_java}" node <<'NODE'
const fs = require("node:fs");

const adapterJava = process.env.ADAPTER_JAVA;
let source = fs.readFileSync(adapterJava, "utf8");

const contextFetch = `        EventStreamProperties properties = new EventStreamPropertiesFetcher(requestExecutor)
                .fetchEventStreamProperties(new PropertiesRequest(input.path("entrypoint").asText(), Lang.TURTLE));`;
const contextFetchWithCache = `        Path contextCachePath = Path.of(input.path("stateDirectory").asText()).resolve("openldes-context.json");
        if (!"ordered".equals(input.path("mode").asText()) && Files.exists(contextCachePath)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> context = JSON.readValue(Files.readString(contextCachePath), Map.class);
            context.put("runId", runId);
            emit(protocol, context);
            return new EventStreamProperties(
                    (String) context.get("eventStream"),
                    (String) context.get("rootNode"),
                    (String) context.get("versionOfPath"),
                    (String) context.get("timestampPath"),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    org.apache.jena.query.DatasetFactory.create());
        }

        EventStreamProperties properties = new EventStreamPropertiesFetcher(requestExecutor)
                .fetchEventStreamProperties(new PropertiesRequest(input.path("entrypoint").asText(), Lang.TURTLE));`;

const contextEmit = `        emit(protocol, context);
        return properties;`;
const contextEmitWithCache = `        if (!"ordered".equals(input.path("mode").asText())) {
            Files.writeString(contextCachePath, JSON.writeValueAsString(context));
        }
        emit(protocol, context);
        return properties;`;

if (!source.includes(contextFetch)) {
  throw new Error("Could not find OpenLDES adapter context fetch block to patch");
}
if (!source.includes(contextEmit)) {
  throw new Error("Could not find OpenLDES adapter context emit block to patch");
}

source = source.replace(contextFetch, contextFetchWithCache);
source = source.replace(contextEmit, contextEmitWithCache);
fs.writeFileSync(adapterJava, source);
NODE

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

printf '\nConformance evidence: %s\n' "${report_dir}"
exit "${status}"
