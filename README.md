# iControl

A custom Android TV home launcher built with Kotlin and Compose for TV. It has a clean, large, easy-to-read layout for elderly users: the focused item grows 1.3x, rows are centred, and the text is big.

Built for a OnePlus TV (Android 11, 1080p), but it should work on any Android TV device.

## Screenshots

| Home screen (favorite app focused) | System row |
| --- | --- |
| ![Home screen](docs/screenshots/home.png) | ![System row](docs/screenshots/system-row.png) |

| All apps (rotating ring) | Apps manager |
| --- | --- |
| ![All apps](docs/screenshots/all-apps.png) | ![Apps manager](docs/screenshots/manage-apps.png) |

| USB drive menu |
| --- |
| ![USB menu](docs/screenshots/usb-menu.png) |

### Slideshow

Your own pictures can cross-fade behind the home screen and play as the TV's screensaver. The pictures are the photos sitting directly in `Pictures/iControl` on the TV's own storage. Android TV has no file picker, so a fixed folder is how pictures get in: add them from a USB drive inside the app (below), or copy them yourself, for example with `adb push photo.jpg /sdcard/Pictures/iControl/`. Pictures in sub-folders are ignored, and at most 300 are used.

The **Slideshow** tile, at the end of the Favorite apps row, opens the settings. All the options are equal-width buttons in a column on the left. On the right is a live preview of the slideshow: the picture as it will look behind the home screen, with its name and position, following the visibility and interval you choose. Under the preview is a row of thumbnails of every picture. Move onto one to see it in the preview, and move away to let it cycle again.

### Options

- **Pictures (N):** opens the pictures screen (below).
- **Home background:** on or off. The pictures cross-fade slowly behind the home screen. Each picture is shown whole, never cropped, and a strongly blurred copy of it fills the margins when it is not 16:9. It is off until you turn it on.
- **Picture visibility:** how much of the picture shows through behind the home screen, from 50% to 100% in steps of 10 (90% by default). The rest is a dark layer; at 100% nothing darkens it. The clock, date, memory text and section titles carry a soft shadow so they stay readable on a bright picture.
- **Change every:** steps through 5, 10, 20, 30 and 60 seconds (10 by default).
- **Screensaver:** Android TV has no lock screen, so the screensaver is what shows when the TV is idle. iControl provides one, which plays the same pictures full screen, each shown whole with a blurred copy filling the margins. The button opens Settings, Device Preferences, Screensaver, where you choose it. Or set it over adb:
  `adb shell settings put secure screensaver_components dev.mahesh.icontrol/dev.mahesh.icontrol.SlideshowDream`

### Managing the pictures

The pictures screen shows every picture as a thumbnail (five across), with the buttons in a column on the left.

- **Remove one:** press OK on a picture. It asks first, and only the copy on the TV is deleted.
- **Select:** multi-select. Press OK on each picture to tick it (or **Choose all**), then **Remove N**. **Done** leaves the mode, and so does Back.
- **Remove all:** deletes every picture after one confirmation, which shows the pictures about to go.
- **From USB:** adds pictures from the plugged-in USB drive. A spinner shows while the drive is scanned. The drive's folders appear three across, each with a cover picture, its photo count and how many you have chosen. Press OK on a folder to open it, then OK on each photo you want (or **Choose all** for the folder). Your choices are kept as you move between folders, and **Folders** or Back returns to the list. **Add N** stays disabled until at least one photo is chosen. It copies every chosen photo into the slideshow folder, with a spinner while it works. A photo that is already there is skipped, and one with the same name but different content gets a numbered name, so nothing is overwritten. **Cancel** leaves without adding.

The first time, iControl asks to read, and for adding and removing pictures to change, files on the TV. The USB browser needs the read permission too.

## Requirements

- Android Studio or JDK 17+ with the Android SDK (compileSdk 37)
- Gradle wrapper included (AGP 9.4.1, Kotlin 2.4.21)
- An Android TV with ADB over network enabled

## Build

```powershell
.\gradlew.bat :app:assembleRelease
```

The release build is signed with the debug key, so it installs directly.

## Install and run

```powershell
adb connect <tv-ip>:5555
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n dev.mahesh.icontrol/.MainActivity
```

## Making it the default home app

The stock launcher registers HOME at a higher priority than non-system apps, so setting the home activity alone is ignored. Disable the stock launcher:

```powershell
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

To undo it:

```powershell
adb shell pm enable com.google.android.tvlauncher
```

The system FallbackHome acts as a safety net if iControl fails to start.

## Notes

- `targetSdk` is 29 on purpose, so the USB drive is readable without an "all files access" settings screen.
- `UsbStorage.TEST_ROOT_ENABLED` must be `false` in committed code. It is a test hook that points the USB reader at `/sdcard/iControlUsbTest`, for taking screenshots with synthetic files.
- The Wi-Fi name in the mirror guide needs location permission (Android requires it to read the network name). Without it, the guide says "the same Wi-Fi as this TV" instead.
- The Apps manager needs the `QUERY_ALL_PACKAGES` permission to list disabled apps and apps without a launcher entry.
- The TV's memory use comes from `ActivityManager`. There is no CPU figure: Android 11 stops apps reading `/proc/stat`.
- The slideshow adds and removes pictures in `Pictures/iControl`, which needs `WRITE_EXTERNAL_STORAGE`. Modifier.blur needs Android 12 and the target TV runs 11, so the blurred margins are made by shrinking the picture to a few pixels and stretching it back.
- Do not commit screenshots of the USB drive's photos or videos, of your own pictures, or of the Wi-Fi name. The slideshow screenshots use generated pictures.

## Project layout

```
app/src/main/java/dev/mahesh/icontrol/
  MainActivity.kt, LauncherViewModel.kt   entry point and state
  SlideshowDream.kt                       the screensaver (a DreamService)
  data/                                   app list, preferences, slideshow picture folder
  ui/                                     home screen, dialogs (all apps, apps manager, mirror guide, slideshow), theme
  usb/                                    USB browser (files, photos, videos, slideshow and playback)
docs/screenshots/                         images used in this README
```

## License

iControl is released under the [MIT License](LICENSE).

It does not include or modify any AirPlay receiver code. The AirPlay receiver (AirPlayMirror Lite by NeoYantra) is a separate app that you install yourself, and it has its own terms. App names and logos shown in screenshots (Netflix, JioHotstar, Zee5, Prime Video, YouTube and others) belong to their owners.

Libraries used: AndroidX, Jetpack Compose, Compose for TV, Media3 and Coil, all under the Apache License 2.0.
