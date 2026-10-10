#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out
# The JDK compiler module also works when javac is not on PATH.
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out src/*.java
if [[ "${1:-}" == "--verify" ]]; then
  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -cp out -d out tests/*.java
  java -cp out RegressionTests
else
  java -cp out WebMain "$@"
fi
