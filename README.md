# Samuel Remote Manager

Consent-based Android device status and location sharing.

## Android features implemented
- Firebase Email/Password authentication.
- Foreground location sharing starts only when the phone user taps **Start sharing my location**.
- Audio recording starts only after the phone owner approves the request, grants microphone permission, and taps **Start audio recording**. A persistent microphone notification remains visible.
- Short owner-approved recordings are stored as Base64 M4A entries in the owner’s private Firebase Realtime Database archive. The website lists recordings with playback and owner-controlled deletion. Each recording is limited to 6 MB; Realtime Database has a 1 GB free storage allowance on Spark.
- A persistent Android notification remains visible while sharing is active.
- Location, accuracy, device model, sharing state, and last-seen time are written under the signed-in user's Firebase account.
- The phone owner can stop sharing at any time.

This is not covert monitoring or full remote desktop control. Android requires visible user approval for sensitive features such as screen capture and camera use. Remote input/control is not implemented.

## Firebase setup required
1. Open Firebase Console and select project `samuel-remote-manager`.
2. In **Authentication → Sign-in method**, enable **Email/Password**.
3. In **Realtime Database → Rules**, publish the contents of `database.rules.json`.
4. In **Authentication → Settings → Authorized domains**, add `waiganjonjoroge11-rgb.github.io`.
5. In **Project settings → Your apps**, register a **Web app** and copy its Firebase web configuration. The website needs this web-app configuration before dashboard sign-in can be connected.
6. Build and install the Android APK. Create an account in the app, then sign into the website using the same email and password.
7. On the phone, grant location permission and tap **Start sharing my location**. The dashboard can then display that account's device location.
8. Keep the project on the **Spark** plan if you want to avoid linking a billing account. Audio archives use Realtime Database; short clips are capped at 6 MB each. The free Realtime Database allowance is 1 GB stored and 10 GB/month downloaded. Large audio archives can use this allowance quickly.

## Database security
The included rules restrict each signed-in account to its own `users/{uid}` data. Do not replace these with public read/write rules. Never publish service-account credentials.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Open the repository's Actions tab to download the `Samuel-Remote-Manager-APK` artifact after a successful run.

