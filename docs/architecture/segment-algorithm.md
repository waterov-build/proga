# Segment Detection Algorithm

## Overview
The `SegmentEngine` uses GPS geo-fencing circles to detect entry and exit of segment start/finish zones.

## Parameters
| Parameter | Default | Description |
|-----------|---------|-------------|
| `START_RADIUS_M` | 30 m | Radius of start geofence |
| `FINISH_RADIUS_M` | 30 m | Radius of finish geofence |
| `MIN_SPEED_MPS` | 1.5 m/s | Minimum speed to arm trigger |
| `DEBOUNCE_MS` | 2000 ms | Cooldown after trigger |

## State Machine
```
IDLE → (enter start zone + speed > MIN) → ARMED
ARMED → (exit start zone) → TIMING
TIMING → (enter finish zone) → RECORDED
RECORDED → IDLE (after DEBOUNCE_MS)
```

## GPS Accuracy
The algorithm uses `Toybox::Position.getInfo().accuracy` to skip fixes worse than `MEDIUM` (≈15 m).
