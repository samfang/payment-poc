#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0

set -e

# OS-specific support
cygwin=false
darwin=false
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true ;;
  MINGW*)  mingw=true ;;
esac

# Resolve base dir (follow symlinks)
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then PRG="$link"
  else PRG="$(dirname "$PRG")/$link"
  fi
done
PRGDIR="$(cd "$(dirname "$PRG")" && pwd)"
MAVEN_PROJECTBASEDIR="$PRGDIR"

MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

# Read distributionUrl
if [ ! -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
  echo "ERROR: .mvn/wrapper/maven-wrapper.properties not found" >&2
  exit 1
fi
DISTRIBUTION_URL=$(grep '^distributionUrl=' "$MAVEN_WRAPPER_PROPERTIES" | sed 's/^distributionUrl=//' | tr -d '\r')

# Derive names
DISTRIBUTION_FILENAME="$(basename "$DISTRIBUTION_URL")"                     # apache-maven-3.9.9-bin.zip
DISTRIBUTION_ID="${DISTRIBUTION_FILENAME%.zip}"                              # apache-maven-3.9.9-bin
MAVEN_INSTALL_DIR_ID="${DISTRIBUTION_ID%-bin}"                               # apache-maven-3.9.9

MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"
DISTRIBUTION_CACHE="$MAVEN_USER_HOME/wrapper/dists/$DISTRIBUTION_ID"
MAVEN_HOME="$DISTRIBUTION_CACHE/$MAVEN_INSTALL_DIR_ID"

# Download and extract if needed
if [ ! -d "$MAVEN_HOME" ]; then
  mkdir -p "$DISTRIBUTION_CACHE"
  ZIP_FILE="$DISTRIBUTION_CACHE/$DISTRIBUTION_FILENAME"
  echo "Downloading $DISTRIBUTION_URL"
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL --retry 3 -o "$ZIP_FILE" "$DISTRIBUTION_URL"
  elif command -v wget > /dev/null 2>&1; then
    wget -q -O "$ZIP_FILE" "$DISTRIBUTION_URL"
  else
    echo "ERROR: neither curl nor wget found" >&2
    exit 1
  fi
  unzip -q "$ZIP_FILE" -d "$DISTRIBUTION_CACHE"
  rm -f "$ZIP_FILE"
fi

MVNCMD="$MAVEN_HOME/bin/mvn"
if [ ! -x "$MVNCMD" ]; then
  echo "ERROR: Maven binary not found at $MVNCMD" >&2
  exit 1
fi

exec "$MVNCMD" "$@"
