# ElementCtrl

**A Matrix chat app for matrix.org and your own server.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ElementCtrl?label=download)](https://github.com/VoidedKN0X/ElementCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ElementCtrl is a Matrix chat client in the spirit of Element X. It works with matrix.org, other public servers and your own self-hosted Synapse, and is built on the same Matrix Rust SDK as Element X, so encryption and verification work the same way.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ElementCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Any server**: matrix.org, other public servers, or your own address
- **Sign in** with OAuth (including creating an account), username and password, or single sign-on
- **End-to-end encryption** by default, with device verification and key backup
- **Messages**: replies, threads, reactions, edits, polls, voice messages, images, videos and files; stickers and GIFs other people send show in the chat
- **Quick gestures**: swipe a message to reply, double-tap to react, and see who has read up to where
- **Chat bubbles** in six styles, from Modern to no bubbles at all (like Element Web)
- **Media and files** for each chat: a photo and video grid and a list of files and voice messages
- **Search inside chats**, including encrypted ones
- **Push notifications through [ntfy](https://f-droid.org/packages/io.heckel.ntfy/)** only, grouped per chat with reply and Mark as read, with a background check every ~15 minutes as a fallback
- **Voice and video calls** in direct chats and rooms, with people on Element X and Element Web: the same Element Call as Element X, bundled in the app, with incoming calls ringing on a full screen, picture-in-picture and speaker, earpiece and Bluetooth audio ([what your server needs](#calls))
- **Link previews and drafts** for every chat
- **Create rooms**, start direct chats, explore public rooms, and manage members and moderation
- **Share to ElementCtrl** from other apps
- **Device list**: rename your signed-in sessions or sign them out
- **App lock** with its own PIN and optional fingerprint unlock; your saved sign-in stays encrypted while the app is locked (with notifications off)
- **Hide in screenshots** and **Hide in recent apps**, two separate switches, both off by default
- **The Ctrl series layout**: **Chats** and **Explore** tabs on a floating glass tab bar (a side rail on tablets), and your profile picture at the top right opens the **You** page with your account, **Settings** and **About**
- Two-pane layout on tablets and foldables

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer on a 64-bit ARM phone
- A Matrix server with **Simplified Sliding Sync** (Synapse 1.114 or newer, Conduit, Conduwuit or Tuwunel)
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/ElementCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds ElementCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ElementCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/ElementCtrl` in Obtainium by hand.

## Getting started

1. Pick a server from the list, or enter your own (`example.com` or `https://matrix.example.com`).
2. Sign in with whatever your server offers.
3. Confirm it's you: tap **Use another device** (approve in Element or ElementCtrl and compare emojis) or **Use your recovery key**, and your chats appear.

Your account, devices, security and recovery, **Settings**, **Sign out** and **About** are on the **You** page: tap your profile picture at the top right of **Chats** or **Explore**. Every path below that starts with **Settings** is there.

## Tips and troubleshooting

- **Notifications:** instant push comes through [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) only (no other push apps). Install ntfy, then turn it on in *Settings › Notifications › Delivery › Instant push with ntfy*. ElementCtrl also checks for new messages about every 15 minutes, with or without ntfy, so a push that doesn't arrive still turns into a notification.
- **Reliable notifications:** let both ElementCtrl and ntfy run unrestricted in the background. *Settings › Notifications* warns when either is battery-restricted and has buttons to fix it. A battery-optimised ElementCtrl gets no network while the phone sleeps, so pushed messages can't be fetched; a battery-optimised ntfy loses its connection to your ntfy server. On OPPO, OnePlus and realme phones, also turn on *Allow background activity* in *App info → Battery* for both apps.
- **Self-hosted ntfy:** your ntfy server's Matrix push gateway (`https://your-ntfy/_matrix/push/v1/notify`, built into ntfy) is used for every push, so your Matrix server must be able to reach it, and the ntfy server needs `base-url` set in its config. ntfy.sh works too. If the gateway can't be found, *Settings › Notifications* shows an error; no other gateway is ever used.
- **What a push contains:** only the room and message IDs and an unread count, never the message, sender or room name. ElementCtrl then fetches and decrypts the message itself from your homeserver. Your homeserver sends the push to the Matrix gateway of your own ntfy server, which passes it to ntfy on your phone.
- **Just your domain doesn't work?** Make sure `https://yourdomain/.well-known/matrix/client` points to your server, or enter the full server address.
- **Not included yet:** spaces, live location and multiple accounts at once.

## Calls

Voice and video calls work in direct chats and rooms, and with everyone on your homeserver who uses Element X or Element Web (MatrixRTC, the calls Element X makes). ElementCtrl uses Element Call, the same call screen Element X has (the same release), built from source and bundled in the app: it runs in your phone's own WebView and nothing of it is loaded from the web. ElementCtrl's build leaves out Element Call's Google reCAPTCHA and sign-up pages, PostHog analytics, Sentry crash reporting, bug-report upload and MediaPipe background blur, none of which calls use (see [Build from source](#build-from-source)).

- **Start a call** with the phone (voice, direct chats) or camera (video) button at the top of a chat. While a call is going on in a chat, a **Call in progress** banner there has **Join**, and the chat list shows a camera next to the chat.
- **Incoming calls** in direct chats ring on a full screen, also over the phone's lock screen, with **Answer** and **Decline**, until the caller hangs up, you answer or decline on another device, or after 90 seconds (then you get a **Missed call** notification). Ringing comes through push, so [instant push with ntfy](#tips-and-troubleshooting) has to be on. On Android 14 and newer, check **Settings › Calls › Full-screen incoming calls**. A call started in a group shows as a normal notification.
- **During a call**: mute, camera on or off, switch camera, and the audio output (speaker, earpiece, wired headphones or Bluetooth) are on the call screen. Going back or home shrinks the call to a picture-in-picture window, and the **Ongoing call** notification has **Hang up**. The camera and microphone are only asked for the first time you start or answer a call.
- **With the app lock on**, answering a call or opening the call screen shows the lock screen first, a call that's already running keeps going while ElementCtrl locks, and the call never shows in Recents. With message content hidden in notifications (the app lock's default), the ringing screen and notifications say "Incoming call" without the caller's name.

### What your server needs

Calls need a MatrixRTC backend next to your homeserver, the same one Element X needs. If your server doesn't announce one, the call button says **Calls aren't set up on your server**, and **Settings › Calls** shows what your server offers.

1. **A LiveKit SFU** ([livekit-server](https://github.com/livekit/livekit)), reachable from the internet: its WebSocket port (7880, behind your reverse proxy), TCP 7881 and the UDP port range it's configured for (e.g. 50100-50200). Its built-in TURN server helps clients on restrictive networks.
2. **The MatrixRTC Authorization Service** ([lk-jwt-service](https://github.com/element-hq/lk-jwt-service)), with `LIVEKIT_URL` (the SFU's `wss://` address), `LIVEKIT_KEY` and `LIVEKIT_SECRET` (the same key as the SFU), and `LIVEKIT_FULL_ACCESS_HOMESERVERS` set to your server name. Synapse needs a `federation` or `openid` listener for it. A common setup is one hostname with `/livekit/jwt` and `/livekit/sfu` behind the reverse proxy (Nginx Proxy Manager works: one proxy host with two custom locations and WebSockets on).
3. **Synapse settings** in `homeserver.yaml`, from [Element Call's self-hosting guide](https://github.com/element-hq/element-call/blob/livekit/docs/self_hosting.md):
   ```yaml
   experimental_features:
     msc3266_enabled: true
     msc4143_enabled: true
     msc4222_enabled: true
   max_event_delay_duration: 24h
   rc_message:
     per_second: 0.5
     burst_count: 30
   rc_delayed_event_mgmt:
     per_second: 1
     burst_count: 20
   matrix_rtc:
     transports:
       - type: livekit
         livekit_service_url: https://matrix-rtc.example.com/livekit/jwt
   ```
4. **Or announce it in `.well-known`**, for homeservers without the transports endpoint: add this to `https://example.com/.well-known/matrix/client`:
   ```json
   "org.matrix.msc4143.rtc_foci": [
     { "type": "livekit", "livekit_service_url": "https://matrix-rtc.example.com/livekit/jwt" }
   ]
   ```

ElementCtrl asks your homeserver for its MatrixRTC service (the transports endpoint first, then `.well-known`) and only ever uses that one: there's no fallback to a public call service.

## App lock

Turn it on under **Settings › Privacy and security › App lock** (tap your profile picture, then **Settings**). It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your saved sign-in is encrypted with the PIN while ElementCtrl is locked**, with the key to its encrypted message store, and the running client is shut down. The unlocking key only exists in memory while the app is unlocked and is wiped the moment it locks. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Picking photos or files, recording a voice message for the first time, sharing, opening a file or link, and the notification and battery settings don't count as leaving, unless you stay away for more than 5 minutes. Shrinking a call to picture-in-picture counts as leaving: the call carries on, and the small window only shows a lock until you unlock.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your saved sign-in. The lock screen offers to sign out on this phone and delete its copy of your chats and keys. With recovery set up, your message history comes back after you sign in again.
- **With the app lock on**, the recent-apps screen always shows a blank card, the connection to your homeserver is closed while ElementCtrl is locked (with notifications off), and message text is hidden in notifications until you choose to show it (**Settings › Notifications**). With notifications on, the background check and push still need your account while ElementCtrl is locked, so your saved sign-in then stays encrypted with the Android Keystore only, as without the app lock, and the connection stays available to them; Settings says so.

Two more switches in the same section work with or without the app lock, and are both off until you turn them on:

- **Hide in screenshots**: screenshots and screen recordings show a blank screen (and so does the recent-apps view).
- **Hide in recent apps**: the recent-apps view shows a blank card instead of ElementCtrl. Screenshots still work.

Password sign-in, the recovery key screens and the lock and PIN screens are always kept out of screenshots and recordings, whatever these switches say.

## Privacy

**Telemetry-free.** ElementCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to your homeserver (and its sign-in page, if your server signs in through the browser), if you turn on push, your ntfy server, and during calls, your server's call service.

- **Push:** when you turn it on, ElementCtrl asks your ntfy server whether its Matrix gateway is there. Pushes then reach the phone through ntfy (see *What a push contains* above).
- **Link previews** are fetched by your homeserver, never by the phone.
- **Calls** connect only to your homeserver (and its domain), the MatrixRTC Authorization Service your homeserver announces, the LiveKit SFU it points to, and the TURN/STUN servers that SFU hands out. The bundled Element Call is built from source without its analytics (PostHog), crash reporting (Sentry), bug-report upload, Google reCAPTCHA and Google MediaPipe code, its fallback call servers are switched off, and the call WebView refuses every other web address. The WebView doesn't ask Google Safe Browsing about pages and sends no usage metrics.
- **The Matrix SDK's crash reporting is switched off**, and release builds don't write the SDK's logs anywhere.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/ElementCtrl.git
   cd ElementCtrl
   ```
2. Build the call screen (Element Call) from source. You need git, [Node.js](https://nodejs.org/) 24 and Corepack 0.34.5 or newer (`npm install -g corepack@latest`, then `corepack enable`), and a Linux or macOS shell (on Windows, WSL):
   ```bash
   bash element-call/build.sh
   ```
   This fetches Element Call at the release pinned in `element-call/upstream.env`, applies ElementCtrl's changes (see [Element Call](#element-call) below), installs its locked dependencies and builds it into `element-call/dist/`. Run it again after updating ElementCtrl if `element-call/` changed. The app build stops with a message if this step is missing.
3. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
4. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

The APK only includes 64-bit ARM code. To run it on an emulator, add `x86_64` to `abiFilters` in `app/build.gradle.kts`.

### Element Call

The call screen is [Element Call](https://github.com/element-hq/element-call) **0.28.0** (commit `9b3c3569ecefbb79cf527ea24e3756ba50553a8f`), the release Element X bundles. Instead of Element's prebuilt package, `element-call/build.sh` builds it from source, and CI does the same for every release (a failed Element Call build means no release). ElementCtrl's changes are all in `element-call/`:

- `patches/0001-build-no-sentry-or-codecov-plugins.patch`: the build no longer adds Sentry's release and debug IDs or uploads anything to Sentry or Codecov.
- `overrides/vite-elementctrl.config.ts`: the embedded build Element X ships, with Sentry's SDK, PostHog's SDK, LiveKit's track processors and Google's MediaPipe replaced by small local stand-ins in `overrides/src/elementctrl/`, so none of them are in the app. No source maps.
- `overrides/src/`: reCAPTCHA, the sign-up page (with Google's privacy and terms links) and account registration, PostHog analytics, bug-report upload and background blur are replaced by versions that do nothing. These are only used by Element Call's own website or on desktop, never by a call inside an app.
- `strip-locales.mjs`: removes the reCAPTCHA texts from the translations.
- `smoke-test.mjs`: after every build, starts Element Call in headless Chrome the way ElementCtrl does and checks that it loads as a widget and tries to reach nothing but the homeserver. The build also fails if any of the removed code shows up again.

Calls work exactly as with Element's package: the same widget URL, the same call service discovery and the same calls as Element X and Element Web. The APK's `assets/element-call/` folder includes Element Call's licence and a `SOURCE.txt` naming the commit and changes it was built from.

## Support the project

ElementCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, the **About** section at the bottom of the **You** page (tap your profile picture) has **Support ElementCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/ElementCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| **ElementCtrl** (this app) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

ElementCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.

Calls use [Element Call](https://github.com/element-hq/element-call) by Element (AGPL-3.0-only), built from source at release 0.28.0 (commit `9b3c3569ecefbb79cf527ea24e3756ba50553a8f`) with the changes in `element-call/` (build script, patches and replaced files, under the same licence); see [Element Call](#element-call). Its licence and a note of the source are in the APK next to it (`assets/element-call/LICENSE-AGPL-3.0` and `SOURCE.txt`). Background blur isn't available in calls.
