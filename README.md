# ElementCtrl

An Android Matrix client in the spirit of Element X, for matrix.org, other public servers and your own self-hosted Synapse. Built with Kotlin and Jetpack Compose on the same [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk) bindings Element X uses, so sync, encryption and verification behave the same way.

## Features

**First start**
- Pick a server: matrix.org, Mozilla, servers you used before, or any **custom / self-hosted** address (`example.com` with `.well-known` discovery, or a full `https://matrix.example.com` URL)
- Sign in with whatever the server offers: **OAuth** through the Matrix Authentication Service (matrix.org and other MAS servers, including **account creation**), **username and password** (classic Synapse), or **single sign-on**
- Clear errors for unreachable servers, bad addresses and servers without sliding sync

**Chats**
- Room list with unread counts, mention highlights, muted and favourite markers, search, and filters for unread, people, rooms, favourites and invites
- Accept or decline invites; long-press a chat to mark read/unread, favourite, set low priority or leave
- Offline mode: chats and messages already synced stay readable without a connection

**Messages**
- Text with formatting and links, notices, emotes (`/me`), images, videos, audio and voice messages, files, stickers, locations and polls
- Swipe right to reply; reply, reply in thread, edit, remove, react (quick reactions or the full picker), copy, copy link, pin, report, view source
- Threads open in their own timeline; reply previews jump to the original message
- Mention people with `@`, send photos, videos and files with captions, create and vote in polls, end your polls
- Profile pictures next to messages (can be turned off)
- Read receipts, typing indicators, "new messages" marker, date dividers, delivery status with retry for failed sends
- Full-screen image viewer with zoom; open, save to Downloads or share any attachment
- Shares from other apps (text, pictures, files) go to the chat you pick

**People and rooms**
- Start an encrypted direct chat by searching the user directory or typing a Matrix ID
- Create private (encrypted) or public rooms with an address, invite people
- Explore public rooms on your server or another one, join by address, open matrix.to links
- Room info: notifications per room, favourite, low priority, members (joined, invited, banned), name, topic and picture for those allowed, enable encryption, share and copy links
- Profiles: send a message, verify, ignore, remove or ban from the room you came from

**Encryption and security**
- End-to-end encryption on by default for DMs and private rooms, with cross-signing and key backup
- Right after sign-in, confirm it's you: pick another signed-in device (compare emojis) or your recovery key, before the chats appear
- Accept verification requests from your other devices; verify other people
- Set up, change or turn off recovery (key storage); reset your identity as a last resort
- Shields on messages from unverified devices; explanations for messages that can't be decrypted
- Session tokens and the database key are encrypted with an Android Keystore key; the SDK's databases are encrypted too

**More**
- Search inside a chat, including encrypted ones, and jump to any result
- Voice messages with waveforms; inline playback for audio
- Link previews and drafts per chat
- Devices list with rename and sign-out; app lock with PIN and fingerprint
- Two-pane layout on tablets and unfolded foldables

**Settings**
- Display name and avatar, manage account (password, devices) on OAuth servers
- Notifications: on/off for this device, default for group and direct chats, mentions, invites
- Light, dark or system theme, Material You colours, hide room events, Enter-to-send
- Ignored people, homeserver and SDK version, sign out

## Requirements

- Android 13 or newer, 64-bit ARM phone (Pixel, Samsung, OPPO, etc.)
- A homeserver with **Simplified Sliding Sync**. Synapse has it built in from **1.114**; Conduit, Conduwuit and Tuwunel also support it. The old sliding-sync proxy isn't supported.

## Notifications

There's no Google push in this app. Two ways to get notified, set in Settings › Notifications › Delivery:

- **Instant push with ntfy** (or any UnifiedPush app). Install [ntfy](https://f-droid.org/packages/io.heckel.ntfy/), pick it in ElementCtrl, and the app registers a Matrix pusher with your homeserver. ntfy.sh works, and so does your own ntfy server (its built-in Matrix gateway is used automatically; otherwise the public UnifiedPush gateway). Only event IDs travel through the push server; ElementCtrl fetches and decrypts the message itself. Notifications are per chat, with reply and mark-as-read.
- **Background check** every ~15 minutes, needing no other app.

### Self-hosted ntfy and Synapse

Your homeserver must be able to reach the gateway URL (`https://your-ntfy/_matrix/push/v1/notify`). If you run ntfy behind a reverse proxy, make sure `/_matrix/push/v1/notify` is passed through.

## Self-hosted Synapse notes

- Entering just your domain works if `https://yourdomain/.well-known/matrix/client` points at the homeserver; otherwise enter the full homeserver URL.
- A server with a private CA works once that CA certificate is installed on the phone (Settings › Security › Encryption & credentials).
- If you use the Matrix Authentication Service, sign-in opens your MAS page in a browser tab and returns to the app.

## Build

GitHub Actions builds a release APK on every push to `main` and publishes it under Releases. To build locally you need JDK 17 and the Android SDK:

```
./gradlew assembleRelease
```

Without signing secrets the release APK is signed with the debug key. To sign with your own key, set these in the repository's Actions secrets or variables: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Only the `arm64-v8a` native library is packaged, to keep the APK small. Add `x86_64` to `abiFilters` in `app/build.gradle.kts` to run it on an emulator.

## Not included yet

Voice/video calls, spaces navigation, sending live location, multiple accounts at once.
