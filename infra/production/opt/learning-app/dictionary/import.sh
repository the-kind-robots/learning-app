#!/usr/bin/env bash
set -euo pipefail

JAR_PATH="${DICTIONARY_IMPORT_JAR:-/opt/learning-app/dictionary/dictionary-import.jar}"
DEFAULT_INPUT_DIR="/var/lib/learning-app/dictionary"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Missing ${JAR_PATH}" >&2
  echo "Set DICTIONARY_IMPORT_JAR or place jar at /opt/learning-app/dictionary-import.jar" >&2
  exit 1
fi

input_dir="${DEFAULT_INPUT_DIR}"
args=()

while (($#)); do
  case "$1" in
    --input-dir)
      if (($# < 2)); then
        echo "--input-dir requires PATH" >&2
        exit 1
      fi
      input_dir="$2"
      shift 2
      ;;
    *)
      args+=("$1")
      shift
      ;;
  esac
done

prepared_dir="${input_dir}"
tmp_dir=""

cleanup() {
  if [[ -n "${tmp_dir}" ]]; then
    rm -rf "${tmp_dir}"
  fi
}
trap cleanup EXIT

if [[ -f "${input_dir}/dictionary-entries.jsonl.gz" || -f "${input_dir}/surface-forms.jsonl.gz" ]]; then
  tmp_dir="$(mktemp -d)"
  cp "${input_dir}/manifest.edn" "${tmp_dir}/manifest.edn"

  for file in dictionary-entries.jsonl surface-forms.jsonl; do
    if [[ -f "${input_dir}/${file}" ]]; then
      cp "${input_dir}/${file}" "${tmp_dir}/${file}"
    elif [[ -f "${input_dir}/${file}.gz" ]]; then
      gzip -dc "${input_dir}/${file}.gz" > "${tmp_dir}/${file}"
    else
      echo "Missing ${file} or ${file}.gz in ${input_dir}" >&2
      exit 1
    fi
  done

  prepared_dir="${tmp_dir}"
fi

exec java -jar "${JAR_PATH}" --input-dir "${prepared_dir}" "${args[@]}"
