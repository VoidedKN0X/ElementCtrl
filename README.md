# ElementCtrl

**A Matrix chat app for matrix.org and your own server.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ElementCtrl?label=download)](https://github.com/VoidedKN0X/ElementCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ElementCtrl is a Matrix chat client in the spirit of Element X. It works with matrix.org, other public servers and your own self-hosted Synapse, and is built on the same Matrix Rust SDK as Element X, so encryption and verification work the same way.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ElementCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src="https://img.shields.io/badge/Add%20to-F--Droid-1976D2?style=for-the-badge&logo=fdroid&logoColor=white" alt="Add the CtrlF-Droid repo to F-Droid" height="56"></a>

## Features

- **Any server**: matrix.org, other public servers, or your own address
- **Sign in** with OAuth (including creating an account), username and password, or single sign-on
- **End-to-end encryption** by default, with device verification and key backup
- **Messages**: replies, threads, reactions, edits, polls, stickers, voice messages, images, videos and files
- **Search inside chats**, including encrypted ones
- **Push notifications through ntfy** (UnifiedPush), no Google services needed
- **Link previews and drafts** for every chat
- **Create rooms**, start direct chats and explore public rooms
- **Device list**, app lock with PIN and fingerprint
- Two-pane layout on tablets and foldables

## Requirements

- Android 13 or newer on a 64-bit ARM phone
- A Matrix server with **Simplified Sliding Sync** (Synapse 1.114 or newer, Conduit, Conduwuit or Tuwunel)

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/ElementCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and login are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds ElementCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ElementCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src="https://img.shields.io/badge/Add%20to-F--Droid-1976D2?style=for-the-badge&logo=fdroid&logoColor=white" alt="Add the CtrlF-Droid repo to F-Droid" height="56"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/ElementCtrl` in Obtainium by hand.

## Getting started

1. Pick a server from the list, or enter your own (`example.com` or `https://matrix.example.com`).
2. Sign in with whatever your server offers.
3. Confirm it's you with another signed-in device or your recovery key, and your chats appear.

## Tips and troubleshooting

- **Notifications:** for instant notifications, install [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) and pick it in *Settings → Notifications*. Without it, ElementCtrl checks for new messages about every 15 minutes.
- **Self-hosted ntfy:** your Matrix server must be able to reach `https://your-ntfy/_matrix/push/v1/notify`.
- **Just your domain doesn't work?** Make sure `https://yourdomain/.well-known/matrix/client` points to your server, or enter the full server address.
- **Not included yet:** voice and video calls, spaces, live location and multiple accounts at once.

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

ElementCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/ElementCtrl/issues) helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| **ElementCtrl** (this app) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |

## License

ElementCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks
