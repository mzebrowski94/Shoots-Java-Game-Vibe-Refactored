#!/bin/sh
# Maven wrapper with offline-toolchain auto-detection.
#
# Resolution order:
#   1. A vendored toolchain under ./tools (JDK 25 + Maven + a pre-populated
#      .m2 repo). Used automatically when present so the sandbox can build
#      fully offline. See tools/BuildToolChainReadme.md for how to populate it.
#   2. Otherwise, fall back to the JDK (JAVA_HOME / PATH) and `mvn` on PATH.
#
# The ralph verify harness and permission allowlist invoke ./mvnw; this keeps
# that contract working in both environments without committing binaries.

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
TOOLS="$ROOT/tools"

# --- 1. Vendored JDK (any tools/jdk* dir containing bin/javac) -----------------
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  for d in "$TOOLS"/jdk*; do
    if [ -x "$d/bin/javac" ]; then
      JAVA_HOME="$d"
      export JAVA_HOME
      PATH="$JAVA_HOME/bin:$PATH"
      export PATH
      break
    fi
  done
fi

# --- 2. Vendored Maven (tools/maven*/bin/mvn) ---------------------------------
MVN=""
for d in "$TOOLS"/maven* "$TOOLS"/apache-maven*; do
  if [ -x "$d/bin/mvn" ]; then
    MVN="$d/bin/mvn"
    break
  fi
done
[ -z "$MVN" ] && MVN="mvn"   # fall back to PATH

# --- 3. Vendored local repo -> force offline build ----------------------------
OFFLINE_ARGS=""
if [ -d "$TOOLS/.m2/repository" ]; then
  OFFLINE_ARGS="-o -Dmaven.repo.local=$TOOLS/.m2/repository"
fi

# --- 4. Redirect target/ off a 9p/virtiofs mount when in the Linux sandbox -----
# Writing class files onto the Windows<->Linux mount causes silent stale-bytecode
# and file-lock failures (see tools/AgentWorkflow.md). When the project lives under
# a sandbox mount (/sessions/.../mnt/), build to a Linux-local dir instead. Windows /
# IDE builds never take this branch and keep using ./target unchanged. Honour an
# explicit -Dbuild.dir / $BUILD_DIR if the caller already set one.
BUILD_DIR_ARG=""
case " $* " in
  *" -Dbuild.dir="*) : ;;                      # caller already chose a build dir
  *)
    if [ -n "$BUILD_DIR" ]; then
      BUILD_DIR_ARG="-Dbuild.dir=$BUILD_DIR"
    else
      case "$ROOT" in
        */mnt/*|/sessions/*)
          _slug=$(printf '%s' "$ROOT" | tr -c 'A-Za-z0-9' '_')
          BUILD_DIR_ARG="-Dbuild.dir=${TMPDIR:-/tmp}/mvn-build/$_slug"
          ;;
      esac
    fi
    ;;
esac

exec "$MVN" $OFFLINE_ARGS $BUILD_DIR_ARG "$@"
