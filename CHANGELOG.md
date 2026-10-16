# Changelog

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
