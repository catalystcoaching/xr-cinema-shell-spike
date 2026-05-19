# Cinema Environment Proof 01

Goal
- Import one free cinema environment candidate
- Preserve startup controller
- Preserve screen assembly
- Add model anchor with free-space fallback
- Retest internal and external content paths

Do not touch
- favourites
- bookmarks
- app library UX
- Moonlight
- media players
- DRM-heavy targets

Baseline
- Screen slot pose: X=0.0, Y=0.0, Z=-1.50
- Screen slot size: 1.6 x 0.9
- Backplate pose: X=0.0, Y=0.0, Z=-1.52
- Backplate size: 3.2 x 1.8

Required flags
- CinemaEnvironmentEnabled = false
- ModelScreenAnchorEnabled = false
