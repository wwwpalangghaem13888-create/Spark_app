# Spark Habit Tracker

A Persian, right-to-left Android habit tracker built with Kotlin, Jetpack Compose, Material 3, and Room.

## Current features

- Create, edit, and delete daily habits.
- Mark habits complete or incomplete for a selected date.
- Persist habits and completion history locally with Room.
- Browse daily completion history with previous/next day navigation.
- View a seven-day completion chart and summary.
- Migrate the original v1 task database to the new habit schema without discarding existing task history.

## Requirements

- JDK 17
- Gradle 8.5
- Android SDK 34

## Build

Run: gradle assembleDebug

The debug APK is generated at app/build/outputs/apk/debug/app-debug.apk.

## CI

GitHub Actions builds the debug APK on pushes to main, master, and repair/**, and on pull requests targeting the main branches. The APK is uploaded as the Spark-debug-apk workflow artifact.

## Scope notes

This version focuses on reliable habit tracking and local persistence. Cloud sync, account sign-in, scheduled notifications, and a home-screen widget are not implemented; the previous widget was only a static placeholder.