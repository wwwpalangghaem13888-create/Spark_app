#!/usr/bin/env bash
# Run as one executable file inside android-emulator-runner.
# Keeping shell state in this file avoids the runner executing YAML lines
# in separate shells (which loses variables such as the Gradle exit status).
set +e
mkdir -p app/build/outputs
adb logcat -c
gradle connectedDebugAndroidTest --stacktrace --info
test_status=$?
adb logcat -d -v threadtime > app/build/outputs/androidTest-logcat.txt 2>&1
log_status=$?
if [ "$log_status" -ne 0 ]; then
  printf 'Unable to collect emulator logcat (exit %s)\n' "$log_status" >> app/build/outputs/androidTest-logcat.txt
fi
exit "$test_status"
