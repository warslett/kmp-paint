# Smoke test: the Fill tool

Verifies the paint-bucket end to end in the running app: region detection,
boundary containment (including slanted boundaries), the click-only
interaction, and the same-colour no-op.

Run headless per `AGENTS.md`: Xvfb on `:99`, software rendering, XTEST input
injection, ImageMagick for verification. Root-window coordinates map to canvas
pixels 1:1, so every coordinate below is a root coordinate.

## Setup

```bash
setsid nohup Xvfb :99 -screen 0 1024x768x24 </dev/null >/dev/null 2>&1 &

cd <repo>
DISPLAY=:99 JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE" \
    setsid nohup ./gradlew run --console=plain </dev/null >/tmp/run.log 2>&1 &
sleep 40

python3 -m pip install --target=/tmp/pylib python-xlib   # if not already present
```

Input helper, `/tmp/drive.py`:

```python
import sys, time
sys.path.insert(0, '/tmp/pylib')
from Xlib import display, X
from Xlib.ext import xtest

d = display.Display(':99')

def move(x, y):
    xtest.fake_input(d, X.MotionNotify, x=int(x), y=int(y))
    d.sync()

def click(x, y):
    move(x, y); time.sleep(0.15)
    xtest.fake_input(d, X.ButtonPress, 1); d.sync(); time.sleep(0.15)
    xtest.fake_input(d, X.ButtonRelease, 1); d.sync(); time.sleep(0.8)

def drag(x1, y1, x2, y2, step=6):
    move(x1, y1); time.sleep(0.2)
    xtest.fake_input(d, X.ButtonPress, 1); d.sync(); time.sleep(0.2)
    dx, dy = x2 - x1, y2 - y1
    n = max(1, int(max(abs(dx), abs(dy)) / step))
    for i in range(1, n + 1):
        move(x1 + dx * i / n, y1 + dy * i / n)
        time.sleep(0.02)
    move(x2, y2); time.sleep(0.2)
    xtest.fake_input(d, X.ButtonRelease, 1); d.sync(); time.sleep(0.8)
```

## Reference coordinates (1024x768 window, default layout)

| Target | Coordinate |
| --- | --- |
| Canvas top-left pixel | (165, 133) |
| Pencil / Brush / Fill buttons | (84, 120) / (84, 164) / (84, 208) |
| BLACK / RED / YELLOW / GREEN / BLUE swatches | (330, 71) / (474, 71) / (546, 71) / (582, 71) / (654, 71) |

Verify the canvas is blank before starting:
`convert base.png -format "%[pixel:p{500,400}]" info:` → `srgb(255,255,255)`.
Capture with `DISPLAY=:99 import -window root <name>.png`.

## 1. Fill inside a closed rectangle

With the default Pencil tool and BLACK colour, draw a closed rectangle:

```python
drag(300, 250, 700, 250); drag(700, 250, 700, 550)
drag(700, 550, 300, 550); drag(300, 550, 300, 250)
```

Then `click(654, 71)` (BLUE), `click(84, 208)` (Fill), `click(500, 400)`.

Expected — only the interior changes:

| Point | Expected | Meaning |
| --- | --- | --- |
| (500, 400) | `srgb(0,0,255)` | centre filled |
| (310, 260), (690, 540) | `srgb(0,0,255)` | filled right up into the corners |
| (500, 251) | `srgb(0,0,255)` | filled right up against the outline |
| (500, 250), (300, 400) | `srgb(0,0,0)` | outline untouched |
| (200, 400), (900, 700) | `srgb(255,255,255)` | no leak to the exterior |

## 2. Re-filling with the colour already there is a no-op

`click(500, 400)` again with BLUE still active, then:

```bash
compare -metric AE fill1.png noop.png null:      # must print 0
```

This also guards the non-termination trap: if the target colour were not
checked, repainted pixels would keep matching and the fill would never end.

## 3. Fill the exterior

`click(546, 71)` (YELLOW), `click(800, 650)`.

| Point | Expected |
| --- | --- |
| (800, 650), (200, 200) | `srgb(255,255,0)` — exterior filled |
| (500, 400), (310, 260) | `srgb(0,0,255)` — interior unaffected |
| (500, 250) | `srgb(0,0,0)` — outline unaffected |

The two regions are separated by a 1px outline, confirming the fill respects
boundaries from either side.

## 4. Slanted boundaries do not leak

The important case: pencil lines are 8-adjacent, so a diagonal stroke has 1px
diagonal gaps that an 8-connected fill would squeeze through. Draw a diamond
and fill it:

```python
click(84, 120); click(330, 71)            # Pencil, BLACK
drag(840, 220, 920, 300); drag(920, 300, 840, 380)
drag(840, 380, 760, 300); drag(760, 300, 840, 220)
click(84, 208); click(582, 71)            # Fill, GREEN
click(840, 300)
```

| Point | Expected |
| --- | --- |
| (840, 300), (840, 240), (800, 300) | `srgb(0,128,0)` — interior filled |
| (770, 240), (910, 370) | `srgb(255,255,0)` — just outside the slanted edges, unfilled |
| (800, 650) | `srgb(255,255,0)` — the surrounding region did not flood |

If this step turns the whole canvas green, the fill is using 8-connectivity.

## 5. Dragging does not re-fill

Fill is a click tool. Press inside the diamond and drag out across its edge:

```python
click(474, 71)              # RED
drag(840, 300, 500, 650)
```

| Point | Expected |
| --- | --- |
| (840, 300) | `srgb(255,0,0)` — the press filled the diamond |
| (500, 650), (200, 200) | `srgb(255,255,0)` — the dragged-over region is untouched |
| (500, 400) | `srgb(0,0,255)` — the rectangle interior is untouched |

## Cleanup

Kill the app and Xvfb one command at a time (`pkill` with multiple patterns
hangs the shell tool):

```bash
pkill -f kmppaint
kill <xvfb pid>
```

## Last run

All five steps passed on the current build.
