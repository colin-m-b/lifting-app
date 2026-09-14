# Workout Log

A small offline app for logging GreySkull LP lifts, accessories and cardio on an Android
phone. Plain HTML, CSS and JavaScript in `www/`, wrapped as an Android APK with Capacitor.
No accounts, no server, no analytics. All data stays on the phone (IndexedDB). JSON
export is the backup route; CSV export is for reading the log in a spreadsheet.

## Install the Android app

Every push to `main` builds a signed APK and publishes it as the release tagged
`latest`. On the phone:

1. Open the repo's Releases page and download `workout-log.apk`.
2. Open the file. The first time, Android asks to allow installs from your browser;
   allow it. Later downloads install as updates and keep your data.
3. On first launch, allow notifications: the rest timer uses them to ding with the
   screen locked.
4. If dings arrive late, set the app to Unrestricted under Settings → Apps →
   Workout Log → Battery.

Every build is signed with the committed `android/app/lifting.keystore`, which is what
lets a new APK install over the old one. Do not regenerate it, or the phone will refuse
the update until the old app is uninstalled (export your data first if that happens).

## Or use it in the browser

The same code deploys to GitHub Pages from `www/` (Settings → Pages → Source: GitHub
Actions). Open the URL in Chrome and choose Install app. Everything works except the
rest timer's ding while the screen is locked; in the browser it only sounds while the
app is on screen.

## The programme

The four main lifts follow GreySkull LP: two sets of five then a final set to failure
(deadlift is a single 5+ set). Each session you pick one of four days, lighter lift first:

- Overhead Press + Squat
- Bench Press (Machine) + Squat
- Overhead Press + Deadlift
- Bench Press (Machine) + Deadlift

The day chooser shows what you did last time for each lift and the target for today.
Targets follow the standard rule on the last set: 5 to 9 reps adds the increment, 10 or
more adds double, under 5 drops the weight by 10 percent. Increments default to 2.5 kg
for presses and 5 kg for squat and deadlift; edit them per lift in Settings.

Picking a day builds each lift's sets: a warmup ramp (bar, 50%, 70%, 90%) rounded to
the nearest 2.5 kg (adjustable in Settings), then the working sets. Tap a set's label to flip it between
warmup and working. Warmups are excluded from progress charts and from progression.

Barbell lifts show the plates per side. Bar weight and the plates you own are in Settings.

A rest timer starts when you log reps on a working set of a programme lift (not after
warmups, not for accessories, and not when you correct a number); the Rest button in the
header starts it by hand at any other time, and a Settings toggle makes accessory sets
start it too. It dings at 90 s and
again at 180 s (both adjustable). In the Android app each ding is an exact alarm
scheduled with `AlarmManager`, so it fires with the screen locked or the app in the
background; logging the next set cancels any pending ones. The alarm plays a short
bundled chime (`android/app/src/main/res/raw/`) with `MediaPlayer` and `USAGE_ALARM`
audio attributes, which is what makes it audible on vibrate, over music in earbuds and
under Do Not Disturb; it follows the alarm volume, not the ringer. A notification
cannot do this: some phones mute every notification sound in vibrate mode whatever
the channel says. The browser version falls back to a WebAudio beep that only plays
while the app is on screen. Settings shows which of the three paths is in use. While the Today tab is open
the app also keeps the screen on so the phone does not lock mid-rest (toggle in
Settings; the KeepAwake plugin in the APK, the Wake Lock API in the browser).

When you are done, **Finish workout** at the bottom of Today stops the timer and locks
the day into a summary card; **Reopen workout** brings the editor back. Finished
workouts show a tick in History, where they can also be marked finished or unfinished.

## Screens

- **Today** — choose a lifting day, or skip that and just add accessories or cardio.
  Accessories are freeform: `+ Set` copies the previous set. Cardio takes minutes,
  distance, speed and incline, plus a "warmup before lifting" tick. Each exercise card
  has a sticky note (seat height, grip) that shows every time; programme lifts show what
  each rep count on the last set means for next time. Bodyweight is an optional box
  under the note.
- **History** — every workout, newest first, grouped by week, month or all time with
  sessions, sets, volume and cardio minutes per group. Tap one to edit or delete it. Add
  a past date with the date picker at the top.
- **Progress** — programme lifts: working weight, reps on the 5+ set, and estimated 1RM
  over time. Accessories: best set and volume. Cardio: distance, speed and minutes, with
  warmup sessions hidden by default. Bodyweight is its own entry in the list. Charts are
  spaced by date.
- **Settings** — plate calculator, bar and plates, warmup rounding, rest timer, exercises
  (add, rename, reorder, archive, set increment), units, export JSON or CSV, import JSON,
  delete all data.

## Getting the data onto a laptop

Settings → Backup has two exports. In the APK they are written to the app cache and
handed to the Android share sheet (Filesystem and Share plugins), so you can send the
file straight to Google Drive, email or anywhere else; the browser version downloads:

- **Export CSV** — one row per set (or per cardio session) with date, exercise, weight,
  reps, warmup flag, target and note. Open it in Google Sheets or Excel.
- **Export JSON** — the complete backup, including exercises and settings. This is the
  file **Import JSON** reads back, so keep one somewhere safe.

Nothing syncs on its own; there is no server. Export when you want a fresh copy.

## Files

```
www/index.html            page shell and tab bar
www/style.css             mobile-first styles
www/store.js              IndexedDB store: exercises, workouts, settings, export/import
www/app.js                the four screens, rest timer, native notification bridge
www/sw.js                 service worker for the browser version (bump CACHE on changes)
www/build.js              build number shown in Settings → About; CI overwrites it
www/manifest.webmanifest  PWA manifest
capacitor.config.json     app id, name, web dir
android/                  Capacitor Android project (committed; CI builds it)
android/app/src/main/java/com/colinmb/liftingapp/
                          MainActivity (plugin registration, notification channels),
                          RestAlarmPlugin (schedules the dings with AlarmManager),
                          RestAlarmReceiver (plays the chime on the alarm stream)
android/app/src/main/res/raw/  rest timer chimes
android/app/lifting.keystore  signing key shared by every build
assets/                   source icons for `npx @capacitor/assets`
.github/workflows/        build-apk.yml (APK → release "latest"), pages.yml (www → Pages)
```

## Changing the app

Edit files in `www/`, push to `main`, and download the new APK from Releases. There is
no build step for the web code itself. To preview locally, serve `www/` with any static
server (`python3 -m http.server -d www`). Regenerate icons with
`npx @capacitor/assets generate --android` after changing `assets/`.

## Data shape

```
exercise  { id, key?, name, note?, type: "weights" | "cardio", order, archived,
            program, scheme: "3x5+" | "1x5+", increment, barbell }
workout   { id, date: "YYYY-MM-DD", dayKey, note, finishedAt?, bodyweight?, entries: [entry] }
entry     { id, exerciseId, target, sets: [{ weight, reps, warmup? }] }            // weights
          { id, exerciseId, cardio: { minutes, distance, speed, incline, warmup? } }  // cardio
```
