# ElementCtrl

**A Matrix chat app for matrix.org and your own server.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ElementCtrl?label=download)](https://github.com/VoidedKN0X/ElementCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

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
- **Push notifications through ntfy** (UnifiedPush), no Google services needed, grouped per chat with reply and Mark as read
- **Link previews and drafts** for every chat
- **Create rooms**, start direct chats, explore public rooms, and manage members and moderation
- **Share to ElementCtrl** from other apps
- **Device list**: rename your signed-in sessions or sign them out
- **App lock** with PIN and fingerprint, and hiding chats from Recents and screenshots
- Two-pane layout on tablets and foldables

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer on a 64-bit ARM phone
- A Matrix server with **Simplified Sliding Sync** (Synapse 1.114 or newer, Conduit, Conduwuit or Tuwunel)
- **Google-free:** works without Google Play services, contains no Google libraries that contact Google, and draws emoji with your phone's own emoji font

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

## Tips and troubleshooting

- **Notifications:** for instant notifications, install [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) and pick it in *Settings → Notifications*. Without it, ElementCtrl checks for new messages about every 15 minutes.
- **Reliable notifications:** allow ElementCtrl to run in the background (*Settings → Notifications → Run in the background*). A battery-optimised app gets no network while the phone sleeps, so pushed messages can't be fetched. On OPPO, OnePlus and realme phones, also turn on *Allow background activity* in the app's *App info → Battery*.
- **Self-hosted ntfy:** your Matrix server must be able to reach `https://your-ntfy/_matrix/push/v1/notify`.
- **What a push contains:** only the room and message IDs and an unread count, never the message, sender or room name. ElementCtrl then fetches and decrypts the message itself from your homeserver. Your homeserver sends the push to your push app's own Matrix gateway (ntfy has one built in); if the push app doesn't have one, it goes through the public UnifiedPush gateway (`matrix.gateway.unifiedpush.org`) instead, and *Settings → Notifications → Delivery* says so.
- **Just your domain doesn't work?** Make sure `https://yourdomain/.well-known/matrix/client` points to your server, or enter the full server address.
- **Not included yet:** voice and video calls, spaces, live location and multiple accounts at once.

## Usage stats

ElementCtrl can share anonymous usage stats, **only if you say yes**. It asks once after you sign in, and you can change it any time in **Settings > Usage stats**.

- **Shared:** which screens you open (the kind of screen, like "chat list", "room" or "settings", never which room or person), whether you send a text, voice message, picture or file (only the kind, never the message or the chat), whether you use search, the app version, Android version, phone model, language, phone or tablet, and whether notifications come through push, the background check or are off.
- **Never shared:** your homeserver, your Matrix ID or anyone else's, room names or IDs, what you write, send or search for, how many messages you send in a chat, or any ID that stays the same between sessions.
- **Where:** VKNXWorks' own self-hosted [Aptabase](https://aptabase.com) server. Nothing goes to Google or analytics companies. Like any website, the server sees your IP address; Aptabase uses it only to work out the country and doesn't store it. Your chats themselves still only go to your homeserver.
- **Switching stats off** stops them straight away and throws away anything not yet sent.

**Other connections.** Apart from your homeserver (and the stats above, only if you said yes), ElementCtrl only contacts one other server: when you turn on push, it asks your push app's server (for example your ntfy server) once whether it has its own Matrix gateway. Link previews are fetched by your homeserver, never by the phone, and pushes reach the phone through your push app (see *What a push contains* above).

Builds from source don't include the stats key, so they can't send anything and the setting is hidden. The code is in [`Telemetry.kt`](app/src/main/java/com/vknxworks/elementctrl/telemetry/Telemetry.kt).

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/ElementCtrl.git
   cd ElementCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

The APK only includes 64-bit ARM code. To run it on an emulator, add `x86_64` to `abiFilters` in `app/build.gradle.kts`.

## Support the project

ElementCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, **Settings → About** (tap your profile picture on the Chats screen) has **Support ElementCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/ElementCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| **ElementCtrl** (this app) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

ElementCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks
