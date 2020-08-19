#!/bin/bash
set -e

echo "Starting $0 at $(date)"

WORKING_DIR="$(pwd)"
SCRIPT_DIR="$(cd $(dirname $0) && pwd)"
cd "$(dirname $0)/../../.."
echo "Script running from $(pwd)"

# resolve DIST_DIR
if [ -z "$DIST_DIR" ]; then
  DIST_DIR="$WORKING_DIR/out/dist"
fi
mkdir -p "$DIST_DIR"

export OUT_DIR=$(pwd)/out
export DIST_DIR="$DIST_DIR"

if [ "$STUDIO_DIR" == "" ]; then
  STUDIO_DIR="$WORKING_DIR"
else
  STUDIO_DIR="$(cd $STUDIO_DIR && pwd)"
fi

TOOLS_DIR=$STUDIO_DIR/tools
gw=$TOOLS_DIR/gradlew

JAVA_HOME="$STUDIO_DIR/prebuilts/studio/jdk/linux" $gw -p $TOOLS_DIR publishLocal --stacktrace

export GRADLE_PLUGIN_VERSION=`grep -oP "(?<=buildVersion = ).*" $TOOLS_DIR/buildSrc/base/version.properties`
export GRADLE_PLUGIN_REPO="$STUDIO_DIR/out/repo:$STUDIO_DIR/prebuilts/tools/common/m2/repository"
export JAVA_HOME="$(pwd)/prebuilts/jdk/jdk11/linux-x86/"
export JAVA_TOOLS_JAR="$JAVA_HOME/lib/tools.jar"
export LINT_PRINT_STACKTRACE=true

LOG_SIMPLIFIER="$SCRIPT_DIR/../development/build_log_simplifier.sh"

function buildAndroidx() {
  "$LOG_SIMPLIFIER" $gw -p frameworks/support --no-daemon listTaskOutputs && \
  "$LOG_SIMPLIFIER" $gw -p frameworks/support --no-daemon bOS --stacktrace -Pandroidx.allWarningsAsErrors -PverifyUpToDate && \
  "$LOG_SIMPLIFIER" DIST_SUBDIR="/ui" $gw -p frameworks/support/ui --no-daemon bOS --stacktrace -Pandroidx.allWarningsAsErrors -PverifyUpToDate
}

function exportTransformsDir() {
  echo exporting transforms directory
  destDir="$DIST_DIR/transforms-2/files-2.1"
  mkdir -p "$destDir"
  cp -rT "$OUT_DIR/.gradle/caches/transforms-2/files-2.1" "$DIST_DIR/transforms-2/files-2.1"
}

if buildAndroidx; then
  echo build succeeded
else
  # b/162260809 export transforms directory to help identify cause of corrupt/missing files
  exportTransformsDir
  exit 1
fi
echo "Completing $0 at $(date)"
