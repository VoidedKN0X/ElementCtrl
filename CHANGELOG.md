# Changelog

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
