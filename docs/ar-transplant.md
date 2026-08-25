# AR Transplant — Osprey → khushu-engine fact mapping

Contract for moving Osprey's studio/AR experiences onto engine facts.
**Rendering stays host-app**: the engine supplies physics; SceneView/camera/
assets remain presentation. This document is the seam agreement.

## Fact sources (engine) → AR needs

| Osprey AR behavior | Engine call | Notes |
|---|---|---|
| Sun/moon position in sky dome | `astronomy.sun.position` / `astronomy.moon.position` | topocentric az/alt + RA/dec |
| Sky trajectory arcs (open curves) | `sun.track(..., includePath = true)` / `moon.track(...)` pathPoints | hourly ENU samples, `aboveHorizon` flag for clipping |
| Moon disc rotation (lit side matches sky) | `moon.state().brightLimbTiltDeg` | χ−q at observer — ported behavior-identically from donor |
| ENU unit vectors for scene placement | `CelestialPathPoint.enu` / `MoonTrackSolver.enu(az, alt)` | [East, North, Up] |
| Horizon clipping | `pathPoint.aboveHorizon` (+ donor's −6° visibility rule is host-side) | engine flags; host decides visibility bands |
| Day phase lighting moods | `sun.phases(...)` bands (`golden/blue/twilight/daylight/night`) | threshold-segment model; conventions pinned in `AltitudeConventions` |
| Time scrubbing across a day | `track` pathPoints keyed by epochMs | nlerp between samples host-side if needed |
| Eclipse events (scene triggers) | `moon.nextGlobalSolarEclipse` / `nextLunarEclipse` | facts only |

## Explicitly NOT in the engine

- Camera/ARCore/sensor fusion (donor `AstroArCameraView`, `EarthArAlignment`)
- Meshes/horizon rings (`generateHorizonRing`) — regenerate host-side from ENU facts
- Closest-point-on-path picking (`findClosestPointOnSphericalPath`) — pure
  geometry over returned pathPoints; re-add as a host utility if needed
- Emoji/icon assets, scene tuning sheets

## Migration checklist (host session)

1. Host consumes `sun.track`/`moon.track` pathPoints; deletes its private ephemeris calls
2. Replace donor `EarthVector` usage with `CelestialPathPoint.enu`
3. Bright-limb rotation reads `MoonState.brightLimbTiltDeg` (χ−q already normalized [0,360))
4. Phase-mood lighting switches on `SolarDayPhases` band membership tests
5. Delete donor duplicates: `AstroEphemerisEngine`, `SolarEphemeris`,
   `SunMoonCalculator`, `LunarEphemeris` (all superseded)
