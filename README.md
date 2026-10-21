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
- **Link previews and drafts** for every chat
- **Create rooms**, start direct chats, explore public rooms, and manage members and moderation
- **Share to ElementCtrl** from other apps
- **Device list**: rename your signed-in sessions or sign them out
- **App lock** with its own PIN and optional fingerprint unlock; your saved sign-in stays encrypted while the app is locked (with notifications off)
- **Hide in screenshots** and **Hide in recent apps**, two separate switches, both off by default
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

## Tips and troubleshooting

- **Notifications:** instant push comes through [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) only (no other push apps). Install ntfy, then turn it on in *Settings → Notifications → Delivery → Instant push with ntfy*. ElementCtrl also checks for new messages about every 15 minutes, with or without ntfy, so a push that doesn't arrive still turns into a notification.
- **Reliable notifications:** let both ElementCtrl and ntfy run unrestricted in the background. *Settings → Notifications* warns when either is battery-restricted and has buttons to fix it. A battery-optimised ElementCtrl gets no network while the phone sleeps, so pushed messages can't be fetched; a battery-optimised ntfy loses its connection to your ntfy server. On OPPO, OnePlus and realme phones, also turn on *Allow background activity* in *App info → Battery* for both apps.
- **Self-hosted ntfy:** your ntfy server's Matrix push gateway (`https://your-ntfy/_matrix/push/v1/notify`, built into ntfy) is used for every push, so your Matrix server must be able to reach it, and the ntfy server needs `base-url` set in its config. ntfy.sh works too. If the gateway can't be found, *Settings → Notifications* shows an error; no other gateway is ever used.
- **What a push contains:** only the room and message IDs and an unread count, never the message, sender or room name. ElementCtrl then fetches and decrypts the message itself from your homeserver. Your homeserver sends the push to the Matrix gateway of your own ntfy server, which passes it to ntfy on your phone.
- **Just your domain doesn't work?** Make sure `https://yourdomain/.well-known/matrix/client` points to your server, or enter the full server address.
- **Not included yet:** voice and video calls, spaces, live location and multiple accounts at once.

## App lock

Turn it on under **Settings > Privacy and security > App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your saved sign-in is encrypted with the PIN while ElementCtrl is locked**, with the key to its encrypted message store, and the running client is shut down. The unlocking key only exists in memory while the app is unlocked and is wiped the moment it locks. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Picking photos or files, recording a voice message for the first time, sharing, opening a file or link, and the notification and battery settings don't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your saved sign-in. The lock screen offers to sign out on this phone and delete its copy of your chats and keys. With recovery set up, your message history comes back after you sign in again.
- **With the app lock on**, the recent-apps screen always shows a blank card, the connection to your homeserver is closed while ElementCtrl is locked (with notifications off), and message text is hidden in notifications until you choose to show it (**Settings > Notifications**). With notifications on, the background check and push still need your account while ElementCtrl is locked, so your saved sign-in then stays encrypted with the Android Keystore only, as without the app lock, and the connection stays available to them; Settings says so.

Two more switches in the same section work with or without the app lock, and are both off until you turn them on:

- **Hide in screenshots**: screenshots and screen recordings show a blank screen (and so does the recent-apps view).
- **Hide in recent apps**: the recent-apps view shows a blank card instead of ElementCtrl. Screenshots still work.

Password sign-in, the recovery key screens and the lock and PIN screens are always kept out of screenshots and recordings, whatever these switches say.

## Privacy

**Telemetry-free.** ElementCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to your homeserver (and its sign-in page, if your server signs in through the browser) and, if you turn on push, your ntfy server.

- **Push:** when you turn it on, ElementCtrl asks your ntfy server whether its Matrix gateway is there. Pushes then reach the phone through ntfy (see *What a push contains* above).
- **Link previews** are fetched by your homeserver, never by the phone.
- **The Matrix SDK's crash reporting is switched off**, and release builds don't write the SDK's logs anywhere.

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
