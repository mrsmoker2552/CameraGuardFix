# CameraGuard — Final Neumorphism Redesign Report

## Scope
Visual redesign only. Existing map/navigation, camera-warning engine, SOS backend, Firebase contracts, Community data flow, Google sign-in, route logic and HUD logic were preserved.

## Locked palette preserved
- #040521 — deep background
- #081D56 — primary dark surface
- #62AAE5 — primary accent / route / selected state
- #F59A2F — secondary highlight
- Semantic SOS red and success green remain unchanged.

## Global design system
- Added shared neumorphic raised/inset modifiers in `ui/theme/Neumorphism.kt`.
- Increased global corner radii for a softer card/control language.
- Light theme uses soft blue-white surfaces while keeping the locked brand family.
- Dark theme uses navy raised surfaces with deep shadows and subtle edge highlights.

## Main navigation and map controls
- Bottom navigation rebuilt visually as a raised neumorphic dock.
- Selected tabs use recessed/raised surface contrast instead of a flat indicator.
- Map/recenter/layers/options/road-alert controls use consistent rounded-square raised controls and zero Material tonal elevation.
- SOS remains high-contrast red, with soft depth only; emergency semantics were not softened.

## Route screen
- Search surface uses a raised neumorphic container.
- Right-side floating controls share the same rounded-square depth language.
- Route/places bottom sheets use softer larger radii and stronger depth.

## HUD
- HUD mode button uses the new raised dark-neumorphic treatment.
- Existing 3D map/scooter/mini-map behavior was not changed.
- Safety-critical warning visuals remain high contrast.

## Settings
- Section headers use raised neumorphic panels.
- Toggle cards use raised depth and selected-state tinting.
- Warning distance selectors use raised control treatment.
- Nearby-riders/settings cards use the same surface system.

## Cameras and warnings
- Camera list rows and metric cards use the new raised card treatment.
- Warning/preview cards use restrained depth while preserving warning colours.

## Community
- Community header is a raised rounded panel.
- Feed/Riders/Messages segmented tabs use raised/recessed selection treatment.
- Join card, feed composer, post cards, rider cards and message rows use the same neumorphic system.
- Message composer fields use recessed/inset treatment.
- Message bubbles use soft depth while retaining sender/receiver distinction.

## Onboarding / vehicle selection
- Vehicle cards, onboarding information cards and primary actions were updated to match the neumorphic system.

## Verification
- ZIP structure/integrity verified after changes.
- Full Android Gradle compile could not be executed in this environment because the Gradle wrapper attempted to fetch `gradle-9.3.0-bin.zip` from `services.gradle.org`, which is not reachable here.
- No claim of successful APK compilation is made; build should be verified in Android Studio.
