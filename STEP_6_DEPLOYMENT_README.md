# Step 6 — Privacy migration (Spark / no billing)

IMPORTANT: This is an experimental privacy build, NOT verified on a device or Firebase emulator. DO NOT deploy the included rules yet.

Public /presence now writes cell-centre coordinates, zero heading and zero speed; exact location is written to UID-only /privatePresence. Public rider markers are approximate; road names, speed and direction are unavailable. 50 km is a UI display filter, NOT an access-control guarantee. Cell-centre locations can still reveal a rider’s approximate area, and successive cells can reveal travel.

Compatibility: old app builds continue writing exact GPS to /presence and will fail under new rules. Existing exact GPS records remain in /presence until migrated/deleted. A safe deployment requires all old clients to be blocked/upgraded, migration or deletion of legacy /presence data, validation of Firebase query-rule behavior, and an authorized cutover. Do not publish rules or claim privacy until this is done.

The free architecture does not support secure server-enforced 50 km proximity. Cloud Functions and paid SMS remain disabled. Accident global push has been disabled in this build; approximate community accident flag requires a separate safe design.

Check: Android Studio build; Firebase emulator rule tests for unauthorized private reads, query-only public reads, legacy exact records, disconnect cleanup; two-device map/consent test. Existing camera warning and route code was not intentionally changed.
