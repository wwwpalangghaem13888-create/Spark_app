#!/usr/bin/env bash
# Wait for a stable Android emulator before starting database migration tests.
# The runner's optional animation-disabling commands can intermittently break ADB
# on cold boots, so the workflow leaves animations enabled and this script performs
# its own readiness check before Gradle touches the device.
set +e
mkdir -p app/build/outputs

adb start-server
device_ready=0
for attempt in $(seq 1 90); do
  device_state="$(adb -s emulator-5554 get-state 2>/dev/null)"
  if [ "$device_state" = "device" ]; then
    boot_complete="$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [ "$boot_complete" = "1" ]; then
      device_ready=1
      break
    fi
  fi
  if [ $((attempt % 6)) -eq 0 ]; then
    echo "Waiting for emulator/ADB readiness: attempt $attempt/90"
    adb devices -l
  fi
  sleep 5
done

if [ "$device_ready" -ne 1 ]; then
  echo "Android emulator did not become ready for instrumentation tests."
  adb devices -l
  adb logcat -d -v threadtime > app/build/outputs/androidTest-logcat.txt 2>&1
  exit 1
fi

# Clear stale logs only after ADB is confirmed online.
adb -s emulator-5554 logcat -c
gradle connectedDebugAndroidTest --stacktrace --info
test_status=$?

adb -s emulator-5554 logcat -d -v threadtime > app/build/outputs/androidTest-logcat.txt 2>&1
log_status=$?
if [ "$log_status" -ne 0 ]; then
  printf 'Unable to collect emulator logcat (exit %s)\n' "$log_status" >> app/build/outputs/androidTest-logcat.txt
fi
exit "$test_status"
