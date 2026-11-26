#!/usr/bin/env bash
set -eu
api="$1"
report=app/build/outputs/androidTest-results
if [ "$api" -ge 26 ]; then
    ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.toolexStorageE2e=true
    if adb shell pm path com.toolex | grep -q package:; then
        adb uninstall com.toolex
    fi
    adb shell test -s /sdcard/Toolex/Toolex-CI-export.png
else
    ./gradlew connectedDebugAndroidTest
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk
if [ "$api" -ge 26 ]; then
    adb shell test -s /sdcard/Toolex/Toolex-CI-export.png
fi
adb shell am start -W -n com.toolex/.MainActivity
sleep 2
mkdir -p "$report"
adb exec-out screencap -p > "$report/home.png"
