# Step 05 — Five full-width category tabs (code change completed)

Source: CameraGuard Steps 01–03 combined archive, with the Step 04 in-progress layer changes retained in this working tree.

Modified `app/src/main/java/com/boss/cameraguard/ui/CameraGuardApp.kt` in both Route screen variants:
- Route navigation (`NavigationScreen`, when route not active): the Petrol/Food/Shops/Hotels/Parks category row now uses `fillMaxWidth()` and five equally weighted buttons, rather than a horizontally scrolling strip.
- Route exploration (`RouteExploreScreen`): the same five categories now use equal-width `OutlinedButton`s beneath the search bar instead of horizontally scrolling `SuggestionChip`s.
- Category click actions, nearby search behavior, and search bar state are unchanged. Both rows retain a small gap and their existing containing-screen horizontal padding.

**Verification:** The two replacements and removal of the original two horizontally scrolling category rows were checked against the source text. No Android Gradle build or physical-device visual test has been run. This does not close the separate Step 04 map-layers task, which remains in progress. No final ZIP was generated.
