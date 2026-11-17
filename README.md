# Samuel Remote Manager

Consent-based Android device status and sharing dashboard.

## Implemented features

- Firebase Email/Password authentication on Android and the website.
- Cloud archives for owner-enabled GPS movement points, camera snapshots, and owner-approved audio recordings, plus device status and remote-request state. Records are stored under the signed-in account's private Firebase Realtime Database path.
- The signed-in dashboard can download a complete account-data JSON backup (including archived media) or a CSV record report (metadata and values, with raw media omitted). Exports include only data currently stored in Firebase; they do not recover data that was never uploaded or has expired/deleted.
- Owner-started high-accuracy GPS movement tracking with a persistent Android notification. While sharing is on, the app records timestamped route points, current speed (km/h), GPS accuracy, optional altitude/bearing, and cumulative trip distance. The dashboard shows live speed/distance and can load the latest 300 saved points for the current tracking session.
- Owner-approved camera sharing: the phone owner must approve the request, grant camera permission, and tap **Start Camera Sharing**. The foreground-service notification stays visible. The website shows the current still image and keeps up to 30 archived snapshots.
- Owner-approved audio recording: the phone owner must approve the on-phone dashboard prompt (or tap **Start audio recording** locally), grant microphone permission if Android asks, and a persistent microphone notification remains visible. An approved remote request opens the recorder and starts capture only after that on-phone approval. Recordings are saved to the owner's private Firebase Realtime Database archive; each recording is limited to 6 MB.
- SMS requests are opened in the phone's SMS app. The phone owner must review and press **Send**. Received texts appear on the dashboard only after the owner explicitly shares them.
- The owner can stop location, camera, and audio sharing on the phone.

This is not covert monitoring or full remote desktop control. The Android app must be open to receive remote camera/audio requests. Sensitive capture requires on-device approval and visible indicators. Remote input/control and silent SMS reading/sending are not implemented.

## Firebase setup

1. In Firebase project `samuel-remote-manager`, enable **Authentication → Sign-in method → Email/Password**.
2. In **Realtime Database → Rules**, publish the contents of `database.rules.json`.
3. In **Authentication → Settings → Authorized domains**, ensure `waiganjonjoroge11-rgb.github.io` is listed.
4. The Firebase client configuration is already included in the Android app and website. If you replace the Firebase project, update both configurations to the same project.
5. Install the APK, sign in or create an account, and use the same account on the website.
6. On the phone, grant location permission and tap **Start sharing my location** to begin GPS tracking. The visible ongoing notification confirms tracking is active; tap **Stop location sharing** to end it. Keep location permission enabled and allow background location/foreground service operation in Android settings if your device prompts. Keep the app open when sending remote camera/audio requests.

The Spark plan avoids requiring a billing account, but usage limits apply. Realtime Database audio storage can grow quickly; each audio recording is capped at 6 MB. Monitor usage in Firebase Console.

## Database security

The included rules restrict each signed-in account to its own `users/{uid}` data. Publish those rules in Firebase Realtime Database. Do not use public read/write rules, and never publish service-account credentials.

## Download and deployment

- Website: https://waiganjonjoroge11-rgb.github.io/Samuel-Remote-Manager/
- Latest APK: https://waiganjonjoroge11-rgb.github.io/Samuel-Remote-Manager/Samuel-Remote-Manager.apk
- GitHub Actions: https://github.com/waiganjonjoroge11-rgb/Samuel-Remote-Manager/actions

GitHub Actions builds a debug APK and publishes it with the website after a successful Android build. Debug APKs are suitable for testing; they are not Play Store releases.
