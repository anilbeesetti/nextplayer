#!/usr/bin/env bash

# Defining a toolchain directory's name according to the current OS.
# Assume that proper version of NDK is installed
# and is referenced by ANDROID_NDK_HOME environment variable
case "$OSTYPE" in
  darwin*)  HOST_TAG="darwin-x86_64" ;;
  linux*)   HOST_TAG="linux-x86_64" ;;
esac

if [[ $OSTYPE == "darwin"* ]]; then
  HOST_NPROC=$(sysctl -n hw.physicalcpu)
else
  HOST_NPROC=$(nproc)
fi

# The variable is used as a path segment of the toolchain path
export HOST_TAG=$HOST_TAG
# Number of physical cores in the system to facilitate parallel assembling
export HOST_NPROC=$HOST_NPROC