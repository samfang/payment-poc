#!/bin/sh
# Apache Maven Wrapper script
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0

set -e

# Determine the base directory (follow symlinks)
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/"$link"
  fi
done
PRGDIR=$(dirname "$PRG")
MAVEN_PROJECTBASEDIR=$(cd "$PRGDIR" && pwd)

MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
MAVEN_USER_HOME="${MAVEN_USER_HOME:-$HOME/.m2}"
MAVEN_WRAPPER_JAR="$MAVEN_USER_HOME/.m2/wrapper/dists/maven-wrapper.jar"

# Read distribution URL from properties
if [ -f "$MAVEN_WRAPPER_PROPERTIES" ]; then
  DISTRIBUTION_URL=$(grep distributionUrl "$MAVEN_WRAPPER_PROPERTIES" | cut -d= -f2- | tr -d '\r')
fi

# Derive the local distribution directory
DISTRIBUTION_ID=$(basename "$DISTRIBUTION_URL" .zip)
DISTRIBUTION_DIR="$MAVEN_USER_HOME/wrapper/dists/$DISTRIBUTION_ID"
MAVEN_HOME="$DISTRIBUTION_DIR/$DISTRIBUTION_ID"

# Download if not cached
if [ ! -d "$MAVEN_HOME" ]; then
  mkdir -p "$DISTRIBUTION_DIR"
  echo "Downloading Apache Maven from $DISTRIBUTION_URL"
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL -o "$DISTRIBUTION_DIR/$DISTRIBUTION_ID.zip" "$DISTRIBUTION_URL"
  elif command -v wget > /dev/null 2>&1; then
    wget -q -O "$DISTRIBUTION_DIR/$DISTRIBUTION_ID.zip" "$DISTRIBUTION_URL"
  else
    echo "ERROR: neither curl nor wget is available" >&2
    exit 1
  fi
  unzip -q "$DISTRIBUTION_DIR/$DISTRIBUTION_ID.zip" -d "$DISTRIBUTION_DIR"
  rm "$DISTRIBUTION_DIR/$DISTRIBUTION_ID.zip"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
