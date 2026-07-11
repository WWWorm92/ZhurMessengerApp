# Pulse Android

Initial standalone Android client scaffold for Pulse Messenger.

## Current state

- Kotlin + Jetpack Compose app shell
- dark theme aligned with the web client
- basic app navigation shell
- login screen wired to current backend
- session token stored locally
- session restore through `/api/auth/me`
- first dialogs list screen through `/api/users`
- DM chat history through `/api/messages/:userId`
- text message send for DM
- first rooms list screen through `/api/rooms`
- base `Socket.IO` manager scaffold for realtime

## Open in Android Studio

1. Open `android-app/` as a project.
2. Let Android Studio sync Gradle.
3. Create or choose an emulator/device.
4. Run the `app` configuration.

## Next implementation steps

1. Connect realtime events to dialogs and DM chat
2. Add room chat history and send flow
3. Add uploads and media rendering
4. Add FCM support on backend and app
5. Add local cache and offline restore
