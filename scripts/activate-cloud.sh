#!/usr/bin/env bash
# Source this file in each new cloud shell; processes and shell exports are not snapshotted.
export LANGUE_TOOLS_DIR="${LANGUE_TOOLS_DIR:-/workspace/tools}"
export JAVA_HOME="$LANGUE_TOOLS_DIR/jdk-21.0.13+9"
export ANDROID_HOME="$LANGUE_TOOLS_DIR/android-sdk"
export ANDROID_USER_HOME="$LANGUE_TOOLS_DIR/android-user"
export ANDROID_AVD_HOME="$LANGUE_TOOLS_DIR/avd"
export GRADLE_USER_HOME="$LANGUE_TOOLS_DIR/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
