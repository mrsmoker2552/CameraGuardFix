# CameraGuard Community implementation

Implemented on top of CameraGuard_FINAL_SOS_BuildFix_V2.

- HUD 3D scooter visual scale increased from 0.82 to 1.42 while keeping the fixed center-lower anchor and lean behavior.
- Bottom navigation label changed from Chat to Community (Groups icon).
- Community is now a gated surface: normal CameraGuard usage remains available to guests, but Community requires a linked Google account.
- Successful Google Community join enables the existing community mode/profile using the current display name.
- Community contains three sections: Feed, Riders, Messages.
- Feed supports live Firestore text posts (max 1200 chars). Posts never include precise location automatically.
- Riders shows the existing online rider list.
- Messages embeds the existing direct/group chat system unchanged.
- Existing “message this rider” deep-link behavior switches Community to Messages.
- Firestore rules extended with owner-only communityMembers and member-only communityPosts collections.

Deployment note: updated firebase-rules/firestore.rules must be published before the new Feed can read/write in production.
