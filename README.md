# Samuel Remote Manager

Consent-based Android device status and location sharing.

## Android features implemented
- Firebase Email/Password authentication.
- Foreground location sharing starts only when the phone user taps **Start sharing my location**.
- Audio recording starts only after the phone owner approves the request, grants microphone permission, and taps **Start audio recording**. A persistent microphone notification remains visible.
- Recordings upload as private M4A files to the owner’s Firebase Storage archive. The website lists recordings with playback and owner-controlled deletion.
- A persistent Android notification remains visible while sharing is active.
- Location, accuracy, device model, sharing state, and last-seen time are written under the signed-in user's Firebase account.
- The phone owner can stop sharing at any time.

This is not covert monitoring or full remote desktop control. Android requires visible user approval for sensitive features such as screen capture and camera use. Remote input/control is not implemented.

## Firebase setup required
1. Open Firebase Console and select project `samuel-remote-manager`.
2. In **Authentication → Sign-in method**, enable **Email/Password**.
3. In **Realtime Database → Rules**, publish the contents of `database.rules.json`.
4. In **Storage → Rules**, publish the contents of `storage.rules`. Enable Firebase Storage and confirm the project’s billing plan supports it; Firebase may require the Blaze plan for Storage.
5. Keep Storage rules owner-scoped. They allow only a signed-in account to read or write files under its own `users/{uid}` folder.
6. In **Authentication → Settings → Authorized domains**, add `waiganjonjoroge11-rgb.github.io`.
7. In **Project settings → Your apps**, register a **Web app** and copy its Firebase web configuration. The website needs this web-app configuration before dashboard sign-in can be connected.
8. Build and install the Android APK. Create an account in the app, then sign into the website using the same email and password.
9. On the phone, grant location permission and tap **Start sharing my location**. The dashboard can then display that account's device location.

## Database security
The included rules restrict each signed-in account to its own `users/{uid}` data. Do not replace these with public read/write rules. Never publish service-account credentials.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Open the repository's Actions tab to download the `Samuel-Remote-Manager-APK` artifact after a successful run.
