#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD="$ROOT/build"
mkdir -p "$BUILD/classes" "$BUILD/test-classes"
find "$ROOT/src/main/java" -name '*.java' -print | sort > "$BUILD/main-sources.txt"
javac -d "$BUILD/classes" @"$BUILD/main-sources.txt"
find "$ROOT/src/test/java" -name '*.java' -print | sort > "$BUILD/test-sources.txt"
javac -cp "$BUILD/classes" -d "$BUILD/test-classes" @"$BUILD/test-sources.txt"
java -cp "$BUILD/classes:$BUILD/test-classes" dev.deliveryproof.ProofDecisionTest
