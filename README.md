![Logo](screenshots/app_icon.png?raw=true "Logo")

# Chromium Android

[![Build Status](https://travis-ci.org/kuoruan/Chromium-Android.svg?branch=master)](https://travis-ci.org/kuoruan/Chromium-Android) [![Release Version](https://img.shields.io/github/release/kuoruan/Chromium-Android.svg)](https://github.com/kuoruan/Chromium-Android/releases/latest) [![Latest Release Download](https://img.shields.io/github/downloads/kuoruan/Chromium-Android/latest/total.svg)](https://github.com/kuoruan/Chromium-Android/releases/latest)

### Introduction

- This is the source code of Chromium Android app.
- All files are from (or generate from) the [Chromium project](https://chromium.googlesource.com/ "Chromium source repo").
- This repository helps you review the most popular browser app on Android.
- You can also build your own Android browser with this repository.

### Start Working

Check out this repository and open the project with [Android Studio](https://developer.android.com/studio/index.html "Download Android Studio").

Now you can build, run, debug as a normal android project.

Notice: If you have something wrong with build, try close instant run in Android Stuido settings.

### Screenshots

![Welcome](screenshots/welcome.png?raw=true "Welcome") ![App Home](screenshots/app_home.png?raw=true "App Home")

![Html5 Score](screenshots/html5_score.png?raw=true "Html5 Score") ![Video Play](screenshots/video_play.png?raw=true "Video Play")

### Source Update

If you want to update Chromium source, these steps may help:

1. [Checking out and building Chromium for Android](https://chromium.googlesource.com/chromium/src/+/master/docs/android_build_instructions.md), to match our build settings, use [args.gn](tools/args.gn)
2. Generate files for Android Studio with [this guide](https://chromium.googlesource.com/chromium/src/+/master/docs/android_studio.md)
3. Sync files with [this script](tools/sync_chromium.sh)
4. Replace all files in project with the new files.

Notice:

- Replace `BASE_DIR` in script with your Chromium src dir.
- Replace `RELEASE_DIR` in script with your build dir.
- `PRO_DIR` is the dir where new files will copied to.
- Sync script may out of date that you need edit it yourself.

### Thanks

This project is based on:

- [https://github.com/JackyAndroid/AndroidChromium](https://github.com/JackyAndroid/AndroidChromium)
- [https://github.com/mogoweb/365browser](https://github.com/mogoweb/365browser)

### Copyright & License

Please see [LICENSE](https://chromium.googlesource.com/chromium/src/+/master/LICENSE).
