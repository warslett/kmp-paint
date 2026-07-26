# kmp-paint

This project is a Kotlin Multiplatform implementation of Microsoft Paint.

## Smoke testing (headless VM)

This VM has no display (`DISPLAY` unset, `XDG_SESSION_TYPE=tty`), so the manual
smoke tests in `docs/*.md` are run **automated and headless**: Xvfb provides a
virtual screen, python-xlib injects synthetic XTEST input (no xdotool/xte
installed), and ImageMagick captures and compares screenshots.

### Setup

```bash
# 1. Virtual display (detached so the shell tool doesn't hang on it):
setsid nohup Xvfb :99 -screen 0 1024x768x24 </dev/null >/dev/null 2>&1 &

# 2. Run the app. Skiko cannot create a GL context under Xvfb, so force
#    software rendering. JAVA_TOOL_OPTIONS (not -D... on the gradlew line)
#    propagates the flag into the forked `run` JVM:
DISPLAY=:99 JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE" \
    setsid nohup ./gradlew run --console=plain </dev/null >/tmp/run.log 2>&1 &

# 3. Input injection library. python3-venv is broken here (no ensurepip), so
#    install into a target dir and use PYTHONPATH instead of a venv:
python3 -m pip install --target=/tmp/pylib python-xlib
```

### Driving the UI

Use `Xlib.ext.xtest.fake_input` with `MotionNotify` / `ButtonPress` /
`ButtonRelease` against display `:99` (root-window coordinates). Pointer
events map to the canvas **1:1**: root coordinate = canvas-origin + bitmap
coordinate, so measure the canvas origin and button/swatch centres once from a
baseline screenshot and reuse them. For drags, interpolate motion in ~6 px
steps with small sleeps; after each action, sleep ~0.8 s before capturing so
the canvas recomposes.

### Verifying

- Capture: `DISPLAY=:99 import -window root shot.png` (ImageMagick; `import`
  and `convert` are installed, `xwd`/`scrot` are not).
- Read screenshots back as images to eyeball them.
- No-op actions: `compare -metric AE before.png after.png null:` must print `0`.
- Exact colours: `convert shot.png -format "%[pixel:p{x,y}]" info:` — the
  palette is opaque, so pixels match swatch colours exactly (e.g. `#0000FF`).

### Cleanup

`pkill -f kmppaint` (the app), then kill the `Xvfb` process. Note `pkill` with
multiple patterns in one command can hang the shell tool — run plain `pkill
<pattern>` / `kill <pid>` one at a time.
