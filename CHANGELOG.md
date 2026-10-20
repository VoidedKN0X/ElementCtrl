# Changelog

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## 1.2.15

### Privacy
- **No more usage stats: ElementCtrl is now telemetry-free.** The optional anonymous usage stats are gone completely, along with the Usage stats setting in Settings and the one-time question after sign-in. The app no longer contacts VKNXWorks' stats server at all, and the settings it used are deleted from your phone when you update. ElementCtrl now only talks to your homeserver and, if you use push, your push app's server (such as your ntfy server).

### Changed
- **README**: a Screenshots section (screenshots are coming soon), and a link preview image (`.github/assets/social-preview.png`) with ElementCtrl's icon, name and summary, for when the repo is shared on Reddit, Discord or elsewhere. It also gets a "Telemetry-free" badge and tag, and a Privacy section listing exactly what ElementCtrl connects to, in place of the old Usage stats section.

## 1.2.14

### Changed
- **One About section in every Ctrl app**: Support, Report a bug, Source code and Licence (GPL-3.0), with the version underneath, at the bottom of Settings (tap your profile picture at the top of Chats). The rows are in the same order, with the same wording and icons, as in the other Ctrl apps. **Report a bug** ("Found a problem or have an idea? Open an issue on GitHub") opens ElementCtrl's issue tracker, **Source code** opens its GitHub page and **Licence** opens the GNU General Public License v3.0. The version ("ElementCtrl 1.2.14 · VKNXWorks") is shown under the section instead of in its own row.
- **Homeserver moved to the Account section.** The row showing your homeserver address (tap to copy, with the "not encrypted" badge for http:// servers) is now under Account instead of About.
- **New Troubleshooting section** with the **Connection log** and the **Matrix Rust SDK** version the app is built with, both of which used to be under About.
- **Sign out** is now just above the About section, which is the last section in Settings.

## 1.2.13

### New
- **Support ElementCtrl** under Settings → About opens VKNXWorks' GitHub Sponsors page in your browser. ElementCtrl is free and open source, and this is the place to sponsor its development if you'd like to. Settings opens when you tap your profile picture at the top of Chats; the new row is the first one under About, above Homeserver.
- The README's **Support the project** section now also points to the **Support ElementCtrl** button.

## 1.2.12

### Fixed
- **Messages that arrive after the app hasn't been used for a while are decrypted much sooner.** When a message's key hadn't reached your phone yet (it was asleep or offline when the message was sent), the app now fetches that key from your encrypted key backup straight away, instead of waiting for another device to send it. This works both for notifications and for messages you see when opening a chat.
- **"🔒 New encrypted message" notifications now update themselves.** After a push, the app keeps syncing that chat for up to 30 seconds in the background (like Element X's "opportunistic sync") so the key can come in, then fetches the message again and replaces the placeholder with the real text in the same notification, without ringing a second time.
- After any message notification, the app also keeps that chat syncing for about 10 seconds, so when you tap the notification the chat is already up to date and decrypted.

## 1.2.11

ElementCtrl is now completely Google-free. Nothing changes in how you use it.

### Privacy

- **Emoji no longer make ElementCtrl ask Google for a font.** A standard Android library inside the app asked Google Play services for a downloadable emoji font every time ElementCtrl started, which could make Google Play services fetch that font from Google. This lookup is now switched off: emoji are drawn with your phone's own emoji font. On phones without Google Play services nothing changes; on other phones, emoji can look slightly different if the phone's built-in emoji font is older.
- **Checked: ElementCtrl is Google-free.** It contains no Google Play services, Firebase, ML Kit, Google Cast or Play Core, and it doesn't load fonts or anything else from Google's servers. It also never switches server addresses by itself: it always uses the address you set up, and only you can change it.

### Changed

- **README: new Google-free line** under Requirements: works without Google Play services, contains no Google libraries that contact Google, and draws emoji with your phone's own emoji font.
- **README brought up to date with ElementCtrl 1.2.10.** The feature list no longer suggests you can send stickers (the sticker and GIF picker was removed in 1.2.1); it now says stickers and GIFs other people send still show in chats. It now also mentions swipe to reply, double-tap to react, read receipts, the six chat bubble styles, the media and files view for each chat, reply and Mark as read on notifications, member management and moderation, sharing into ElementCtrl from other apps, renaming and signing out devices, and hiding chats from Recents and screenshots. Getting started now uses the **Use another device** and **Use your recovery key** buttons by name. The Usage stats section now lists the one other server the app contacts: your push app's server, once, to check for a Matrix gateway when you turn on push. No change to the app itself

## 1.2.10

A privacy review of the new optional usage stats and of every connection the app makes. The stats are still off unless you said yes.

### Security

- **Switching usage stats off now throws away anything not yet sent.** Before, a stat recorded just before you switched them off could still be waiting in the app, and if you switched stats back on within a few seconds it could have been sent after all. Now everything waiting is discarded the moment you switch stats off, so nothing recorded before that point can ever go out.
- **Settings shows when pushes go through the public UnifiedPush gateway.** If your push app has no Matrix gateway of its own (ntfy has one), your homeserver sends pushes through the public gateway run by the UnifiedPush project. A push only ever carries the room and message IDs and an unread count, never the message, sender or room name, but it's still a third party, so *Settings → Notifications → Delivery* now says "through the public UnifiedPush gateway" when that's the case. The README explains what a push contains.

## 1.2.9

### Changed

- **New app icon, matching the rest of the Ctrl series.** The icon is now the same dark keycap as the other Ctrl apps, with two pink chat bubbles, an outlined one behind a filled one with three dots on its screen. The themed (monochrome) icon on Android 13+ home screens uses the same design.
- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) (Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app), [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) (scan and browse documents on Paperless-ngx), [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) (manage your Proxmox VE server) and [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) (monitor and manage your TrueNAS server). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates.

## 1.2.8

### New
- **Optional anonymous usage stats.** After you sign in, ElementCtrl asks once whether you'd like to share anonymous usage stats. Nothing is sent unless you tap **Share stats**, and you can switch it on or off any time in **Settings › Usage stats**.
- **What's shared:** which screens you open (the kind of screen, for example the chat list, a room or Settings, never which room or person), whether you send a text, voice message, picture or file (only the kind of message), whether you use search in the chat list or inside a chat, the app version, Android version, phone model, language, phone or tablet, and whether your notifications come through push, the background check or are off.
- **What's never shared:** your homeserver, your Matrix ID or anyone else's, room names or IDs, who you talk to, what you write, send or search for, how many messages you send in any chat, or any ID that follows you between sessions. Each session gets a new random ID, and stats from before and after you switch them off and on again aren't linked.
- **Where it goes:** to VKNXWorks' own self-hosted server (Aptabase), not to Google or any analytics company. Your messages still only go to your homeserver. Builds you make from source can't send stats at all, and the setting doesn't appear in them.

### Changed
- **PrintCtrl in the Ctrl series list.** The README's table of Ctrl apps now includes [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl), the app for Bambu Lab printers through a self-hosted Bambuddy server, and Spoolman spools.
- The README has a new **Usage stats** section listing exactly what's collected.

## 1.2.7

### Fixed
- **Coming back to the app after a while in the background no longer leaves it disconnected.** While the app sits in the background Android freezes it, and the connection it had open can die without the app noticing. Each start, stop and restart of the connection now has a time limit, so one that never gets an answer can't block the others, and after more than a minute away the app always opens a fresh connection when you return instead of trusting the old one.
- The app now notices when the connection says it's running but hasn't received anything from the server for 40 seconds, and restarts it.
- When the connection keeps failing, the app now also asks the server for a completely new sync session (from the third try), since the old one may have expired during the break.
- **Last resort, automatic reload:** if the app still isn't syncing after 45 seconds on screen while the phone has a working network and your homeserver is answering, it reloads itself, exactly like force-stopping and reopening it, and reopens the chat you were in. This happens at most once every 10 minutes, so a server problem can't cause a reload loop. If app lock is on you'll be asked to unlock again.
- "Online" now means a network that Android has confirmed actually reaches the internet, so Wi-Fi without internet (or a captive portal) no longer counts as a working connection.

### New
- **Reconnect** button on the chat list's connection banner ("Offline", "Connection problem", "Can't reach the server"): reloads the app's connection on the spot, without having to force-stop it in Android's settings.
- **Connection log** in Settings › About: a record of what the connection did (going to the background and back, network changes, every start, stop and restart and how long it took). It contains no messages, names, servers or tokens. If disconnects still happen, tap it and use Copy to share what happened. It's deleted when you sign out.

## 1.2.6

### Fixed
- **The app no longer loses its connection after you switch away and come back.** Leaving ElementCtrl stopped syncing and returning started it again, but the two could overlap: if you came back before the stop had finished, the stop landed last and the app sat disconnected (no new messages, nothing sending) until it was force-stopped. Syncing is now started and stopped from a single place, one step at a time, the way Element X does it, so this can't happen any more.
- After a long break the server may have expired the app's sync session; the app now notices that the connection died and reconnects on its own, waiting a little longer after each failed try (1 second up to about half a minute) so it doesn't hammer the server.
- When Wi-Fi or mobile data comes back (for example after the phone woke up or you left a dead spot), the app reconnects as soon as Android reports a working network, instead of staying in "offline" mode.

### Changed
- Briefly switching to another app (under 3 seconds) keeps the connection open, so coming straight back doesn't need to reconnect.

## 1.2.5

### Fixed
- **Notifications no longer go missing when ElementCtrl hasn't been opened for a while.** Before, a push from ntfy had to be turned into a notification within the few seconds Android gives a sleeping app, and if the phone was in deep sleep the app had no network to fetch the message, so the notification was silently dropped. Now each push is saved first and fetched by a priority background job (the same approach Element X uses), which Android starts straight away and which keeps trying until the network is back, so the notification still arrives.
- If a message really can't be fetched after a few tries (server unreachable, message not found yet), you now get a plain "New message" notification for that chat instead of nothing. Tapping it opens the chat, and it has Mark as read.
- **Lost push registrations are repaired automatically.** Synapse quietly deletes a pusher when the push gateway reports it as unknown (for example after the ntfy server restarted), after which no pushes arrive at all. ElementCtrl now checks at every start and twice a day that the homeserver still has its pusher, and adds it back if it's gone.
- Pushes that arrived while the app couldn't handle them are picked up the next time it starts.

### New
- **Run in the background** in Settings › Notifications shows whether battery optimisation is restricting ElementCtrl, and opens Android's prompt to allow it. Restricted apps get no network in deep sleep, which is the main reason notifications go missing (Element X asks for the same permission).
- On OPPO, OnePlus and realme phones there's an extra shortcut to App info, where ColorOS keeps its own "Allow background activity" and "Auto launch" switches that can also stop notifications.
- A banner on the chat list warns when notifications are on but the app is battery-restricted, with **Allow** and **Not now**. After Not now it only comes back if a message actually had to fall back to a plain notification.
- Choosing ntfy (or another push app) in Settings now asks right away to let ElementCtrl run in the background.

## 1.2.4

### Security
- **Sign-out wipe can't be left half done**: if the app is closed (or the phone turns off) in the middle of signing out, the next start finishes the job, removing any drafts, downloaded or decrypted attachments, files waiting to upload and notification state that were left behind. Before, some of these could stay on the phone after sign-out.
- When the server signs this device out, syncing now stops before the account's data is deleted, so nothing new can be written back into the folders being removed.
- Downloaded and decrypted attachments older than a day are now also removed while the app keeps running, not only when it starts, so they don't stay around for days when the app is never fully closed.
- Error messages shown in the app now also hide tokens and passwords in the formats servers and the Matrix SDK use in JSON and debug output (for example `"access_token": "…"`), and web tokens (JWTs) used by OIDC sign-in.
- The password field when signing out other devices now tells the keyboard not to suggest, learn or remember what you type.

## 1.2.3

### Security
- **Recovery key screens are private**: the recovery key entry after sign-in, the Security & recovery screen and its dialogs (new recovery key, enter recovery key, identity-reset password) can no longer be captured in screenshots, screen recordings or screen sharing, whatever the app-lock setting.
- The recovery key and identity-reset password fields now tell the keyboard not to suggest, learn or remember what you type.
- Copying a new recovery key marks it as sensitive, so Android hides it from the clipboard preview and keyboards don't keep it in their clipboard history.
- If a recovery key doesn't work, the error message never repeats the key, and the field is cleared once recovery succeeds.
- The password sign-in screen is also protected from screenshots and recordings, and the typed password is no longer kept in Android's saved screen state.
- **Reply** and **Mark as read** on message notifications now ask you to unlock the phone first when used from the lock screen, so nobody holding your locked phone can send messages or read receipts as you.
- **Signing out now wipes everything** this device had for the account: besides the session, keys and message databases, also downloaded and decrypted attachments and voice messages, files waiting to upload, leftover databases from unfinished sign-ins, the list of already-notified chats, open notifications, link previews and pictures held in memory. Background checks stop too. The same happens when the server signs this device out, which now also drops the local push registration.
- Pictures (including ones decrypted from encrypted chats) are only ever cached in memory, never written to storage.
- Downloaded attachments are removed from the app's cache after a day, and the plain copies of files and voice messages you send are deleted as soon as they're handed to the encrypted send queue.
- Downloaded attachments are stored under a full hash of their source, so two different files can never be mixed up.
- Device-management requests and the push-gateway check no longer follow redirects (so your access token can't be forwarded to another server) and refuse oversized replies.
- Error messages shown in the app have anything that looks like a token, password or URL query removed.
- Release builds no longer write the Matrix SDK's logs, or the app's own info and warning logs, to the system log.

## 1.2.2

- Version numbers are now plain, with no build number on the end, and a new version only comes out when something in the app actually changes. Nothing else in the app has changed.

## 1.2.1

- Chat list: the time of the last message now always sits flush on the right
- **Double-tap to react**: double-tap a message to react with ❤️ (or another emoji, or off, in Settings › Appearance), with a little pop
- Quick reactions in the message menu start with the emojis you use most recently (shared with Element through your account)
- **Media and files** in chat info, for direct chats and rooms: a photo and video grid and a list of files, audio and voice messages, by month
- **Seen by**: small pictures under messages show who has read up to there; tap them for names and times (Settings › Appearance › Seen by)
- Swipe a message to the right to reply: the reply arrow fills in with a little buzz once you've pulled far enough, and the keyboard opens ready to type
- Profile pictures next to messages, in direct chats and next to your own messages too (Settings › Appearance › Profile pictures to turn off)
- Messages sent close together are grouped: the profile picture sits by the last message of the group, and only that bubble keeps its tail
- Fixed a black screen that could appear when going back or switching screens quickly, until the app was restarted
- The message box island is evenly balanced: the + and send buttons line up with the text and sit the same distance from the edges
- The "New messages" line goes away once you've read to the end, instead of staying put; marking a chat read from a notification moves it too
- "Mark as read" on every message notification, including when message content is hidden
- Removed the sticker and GIF picker, Tenor GIF search and "Add to my stickers". Stickers and GIFs other people send still show in chats
- Verification now happens right after sign-in, before your chats appear: choose **Use another device** (approve in Element or ElementCtrl and compare emojis) or **Use your recovery key**. Requests started from your other device are answered on the same screen. An unverified device is asked again on every start until it's verified
- Security hardening:
  - Plain-http homeservers need an explicit "Not encrypted" confirmation and show a badge in Settings; no silent https→http downgrade
  - Files shared into ElementCtrl must come from another app (no file: links or ElementCtrl's own files)
  - While locked, chats aren't drawn at all (nothing for accessibility services to read); the app never leaves a snapshot in recent apps
  - Message notifications show only "New message" on the lock screen; with app lock on, message content is hidden in notifications too (new setting: Show message content in notifications)
  - PIN lockout survives clock changes and reboots, with longer waits after repeated wrong PINs; new PINs need at least 6 digits (existing PINs keep working)
  - Fingerprint unlock is tied to a hardware-backed key (turn it on again once after updating)
  - Locations only open as map locations; message links are limited to web, matrix and email addresses, and a link whose text shows a different address asks before opening
  - Draft previews and the Tenor key are stored encrypted; nothing is included in Android backups or device transfers
  - Pushes for rooms you're not in are ignored
- New app icon: twin chat bubbles on deep purple, matching the other Ctrl apps; themed (monochrome) icon and notification icon updated to match
- The first-start server list now offers just matrix.org and Mozilla, plus servers you used before and custom / self-hosted addresses

## 1.2.0

- **Instant push notifications** through ntfy or any UnifiedPush app (Settings › Notifications › Delivery). Messages are fetched and decrypted on the phone; notifications are grouped per chat with inline reply and mark-as-read
- **Search inside a chat**, including encrypted chats, using an on-device index; tap a result to jump to it
- **Voice messages**: tap the mic when the message box is empty, with a live waveform; voice and audio play inline with a seekable waveform
- **Link previews** under messages (fetched by your homeserver; off in encrypted chats unless you turn it on)
- **Drafts** are kept per chat and thread, and shown as "Draft: …" in the chat list
- **Devices**: see your signed-in sessions, rename them and sign them out (password or your account page)
- **App lock** with PIN and fingerprint, lock delay and hiding from recents, same as the other Ctrl apps
- **Stickers** from Matrix image packs (yours and rooms'), "Add to my stickers" on any picture, and **GIFs**: animated playback, sending from the gallery, and Tenor search with your own key
- **Tablet and foldable layout**: chat list and conversation side by side

## 1.1.0

- Chats follow new messages as they arrive, unless you've scrolled up to read; then a badge on the jump-down button counts what's new
- The message box is now a floating frosted-glass island, like the other Ctrl apps, with replies, edits and @-mention suggestions in a small island above it
- Choose a chat bubble design in Settings › Appearance › Chat bubbles: Modern, Round, Sharp, Outlined, Vivid or No bubbles (Element Web style), each with a live preview

## 1.0.1

- After signing in on an account that already uses encryption, a "Confirm it's you" step asks you to verify the device (another device or recovery key), like Element X. Unverified devices can't get message keys, which made messages slow to decrypt or stay locked
- Messages that can't be decrypted now say when verifying or entering your recovery key would fix them, with a button to do it
- Fixed background sync running non-stop (and notification checks being skipped) when Android started the app just for a notification check

## 1.0.0

First release.

- Server picker on first start with popular public servers and custom/self-hosted addresses
- Sign-in with OAuth (Matrix Authentication Service, including registration), password or SSO
- Room list with filters, search, invites and quick actions
- Timeline with rich messages, media, polls, threads, replies, edits, reactions, read receipts and typing
- Attachments, mentions, polls and shares from other apps
- Create rooms, start DMs, explore and join public rooms, matrix.to links
- Room and member management, profiles, ignore, moderation
- End-to-end encryption with emoji verification, recovery key and identity reset
- Settings for profile, notifications, appearance; background notifications every ~15 minutes
