# Changelog

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

README only; the app itself hasn't changed, so this doesn't make a new release.

### Changed

- **PrintCtrl in the Ctrl series list.** The README's table of Ctrl apps now includes [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl), the app for Bambu Lab printers through a self-hosted Bambuddy server, and Spoolman spools.

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
