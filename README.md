# JRC Fitness — calibration, fixed jump detection, stats

## Where the files go
Drop everything under `app/` into your existing module (same paths), and
add the four `<activity>` lines from `AndroidManifest_additions.xml` to
your `AndroidManifest.xml`. `activity_main.xml` replaces your current one;
the other three layouts and all `.java` files are new. Nothing outside
`com.JRC.fitness` is touched — no new dependencies, all `org.json` /
Android SDK.

## What was actually wrong

**Squats counting from tiny vibration** — the old thresholds (8.0 / 11.0
around gravity's 9.8) were a very narrow, fixed band that any phone
jostle could cross, and nothing stopped two crossings a few ms apart from
both counting.

**Jumps not counting** — `getMaxAmplitude()` was compared to one fixed
number (15000) that doesn't match real mic gain across devices, it was
*level*-triggered instead of edge-triggered (so a loud stretch of audio
could count more than once, or a phone whose baseline noise sits near
15000 would never cross it at all), and `setOutputFile("/dev/null")` is
flaky on some OEM builds.

## The fix: calibration + shared hysteresis detector
`RepDetector` (`RepDetector.java`) is now the single place rep-counting
logic lives, used identically for squats and jumps: a smoothed signal has
to fall below `low`, then rise above `high` (edge-triggered), and any
candidate faster than `minIntervalMs` after the last rep is dropped as
noise.

`CalibrationActivity` gets `low`, `high`, and `minIntervalMs` from the
user instead of guessing: tap Start, do a fixed number of reps (10
squats / 20 jumps) at a normal pace, tap Stop. It computes the resting
baseline and the actual movement range from what was recorded and derives
personalized thresholds from that. It can be re-run any time from the
"Recalibrate" link on either training screen — recalibrating overwrites
the old thresholds for that exercise only.

## Separate windows + auto-start
`SquatTrainingActivity` and `JumpTrainingActivity` are now separate
screens (sharing `TrainingActivity` for the timer/stats/goal plumbing,
`activity_training.xml` for layout), so only the relevant sensor runs at
a time. Each opens in a "Get ready" state — the clock and rep count don't
start until the first real rep is detected, matching "auto start when the
user starts the reps." Tapping Stop ends the session and saves it.

## Stats & goals
Every finished session (type, rep count, duration) is saved via
`DataStore` (SharedPreferences + JSON, no DB). `StatsActivity` shows, per
exercise: session count, total reps, average reps/session, best pace, and
a scrollable history of recent sessions with reps/sec for each. It also
lets you set a target rep count per exercise; the training screen shows
live progress against it while you train.

## First run
`MainActivity` now routes: tapping Squats/Jump Rope goes straight to
calibration if that exercise has never been calibrated, otherwise
straight into training.
