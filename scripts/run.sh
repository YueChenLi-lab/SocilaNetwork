#!/usr/bin/env bash
set -euo pipefail
java_command=java
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_command="$JAVA_HOME/bin/java"
  if [[ ! -x "$java_command" ]]; then
    echo 'JAVA_HOME must point to an installed JDK 17 or newer (not its bin directory).' >&2
    exit 1
  fi
fi
# Explicit relative CSV arguments are relative to the caller, not the script.
if [[ -n "${1:-}" && "$1" != "--verify" && "$1" != /* ]]; then
  set -- "$PWD/$1" "${@:2}"
fi
cd "$(dirname "$0")/.."
mkdir -p out
# The JDK compiler module also works when javac is not on PATH.
"$java_command" -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -d out src/*.java
if [[ "${1:-}" == "--verify" ]]; then
  "$java_command" -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -cp out -d out tests/*.java
  "$java_command" -cp out RegressionTests
else
  "$java_command" -cp out WebMain "$@"
fi
