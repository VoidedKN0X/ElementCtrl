# Changelog

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## 1.6.6

### Fixed

- **Calls from a chat with one person ring again.** When you called someone from a one-to-one chat, their phone sometimes only showed a "call started" message instead of ringing like a phone call. ElementCtrl counted anyone who had once been invited to the chat but never joined, so a single old invite made it treat the chat as a group room and start the call without ringing. Now only the people who have actually joined count, the same way Element Desktop decides, so a chat between the two of you rings the other person's phone even if an old invite is still hanging around.
- **Those chats also look like chats again.** The same old invites made some one-to-one chats appear under Rooms instead of People, show room settings (with Invite and room name options) instead of the other person's profile when you tap the name, and show their notifications as group-room notifications. They now show as a normal chat with that person everywhere in the app.

## 1.6.5

### Fixed

- **Fingerprint unlock works again after you leave the app.** Once you'd left ElementCtrl with the back gesture or by swiping home, the lock screen no longer asked for your fingerprint when you came back, and tapping **Use fingerprint** did nothing either; only the PIN worked until the app was force-closed. The fingerprint prompt was asked for while the app was still coming back on screen, and the phone dropped it without answering, which left ElementCtrl waiting for an answer that never came. Now the prompt only opens once ElementCtrl is fully back on screen, a prompt that never answered can't block the next one, and the lock screen offers your fingerprint again every time you come back to it, not just the first time after it locked.

## 1.6.4

### Security

- **Room links ask before you join.** Opening a matrix.to or matrix: link to a room you're not in joined it straight away, without asking, and a link to a room you were invited to accepted the invite. Now ElementCtrl asks **Join …?** first, with the room's name, topic and number of members when your server can show them, and the room's address so you can check where the link really goes; nothing happens until you tap **Join**, and **Cancel** drops the link. A link to a room you're invited to now opens that room's invite, where you choose **Accept** or **Decline** yourself. A link that arrives while ElementCtrl is locked waits until you unlock, and then still asks.
- **The app lock now also works during a call in picture-in-picture.** ElementCtrl never counted shrinking a call to the small picture-in-picture window as leaving the app, so it never locked during a call, however long it went on. Now it does: with **Lock again: as soon as you leave** it locks the moment the call goes to picture-in-picture, and with a lock time it counts from then. The call carries on; the small window shows a lock instead of the call, and you unlock when you open it full screen or go back to ElementCtrl.
- **Shared files can't point back to ElementCtrl's own files any more.** A share from another app with an address like "content://0@…" (with a user number in front) slipped past the check that stops other apps from making ElementCtrl read and send its own private files, such as your chat database. That form is now recognised and refused too.

### Privacy

- **No hidden dependency list in the app file any more**: the build tools used to tuck an encrypted list of the libraries ElementCtrl is built with into the APK's signature block. Only Google can read it, so nobody checking the app could see what it holds. It's now left out, the same as in the other Ctrl apps. Nothing changes in how the app works.

## 1.6.3

### Changed

- **Devices and Security & recovery use the series cards**: their rows now sit in rounded cards with a heading above each one (This device, Other devices, Verify this device, Recovery), with round icons, the same as Settings, the You page and the other Ctrl apps. Before, they were flat lists with small section labels. Everything on them works the same: tap a device for its details, rename or sign out; **Sign out all other devices** and **Manage on your account page** (with its explanation under the card) are in cards of their own; your Session ID and Session key are still copied with a tap.
- **Whole sentences instead of joined pieces, ready for translations**: thread summaries ("2 replies · latest message"), chat previews ("🖼 Image", "📎 file name"), "removed by … : reason", the chat bubble setting's summary, "Search this chat", "This is the beginning of this room", "Verify …" (now with the person's Matrix ID when they have no display name, instead of "user") and the " · " between details are now each one piece of text.

### Fixed

- **Thread summaries count correctly**: "1 reply" and "2 replies" now come from the app's plurals.

## 1.6.2

The last screens now look like the rest of ElementCtrl and the other Ctrl apps, and all text is ready for translations.

### Changed

- **Devices, Security & recovery and the other inner screens use the series top bar**: Devices, Security & recovery, Verify this device / Verify user, Recovery key, Sign in, New chat, Create room, Room info / Chat info, People, a person's Profile, Media and files, and the "Room isn't available yet" screen now have the same plain top bar (back arrow and title on the app background) as You and Settings. Before, they used a different, older bar.
- **Your profile picture looks the same as in the other Ctrl apps**: at the top right of every tab and at the top of the You page it's your Matrix avatar when you have one, otherwise the first letter of your name on a softly tinted circle in the app's colour (before, your initial in white on a randomly coloured circle). Other people keep their coloured initials in chats.
- **All text is now in one place, ready for translations.** About 500 pieces of text that were written straight into the code, including the whole chat screen (composer, message menu, polls, voice messages, typing and read indicators, attachments, link warnings), room info and members, devices, security and recovery, verification, sign-in, push setup, notifications and error messages, now come from the app's text resources. The wording stays the same.

### Fixed

- **Counts read correctly in the singular and plural**: "1 member", "1 vote", "Final results · 1 vote", "1 new message" in notifications, "Signed out 1 device" and "Sign out 1 device?" instead of "1 members", "1 votes" and so on.

## 1.6.1

The call screen is now built from source, without Google's code. Calls work exactly as before.

### Privacy

- **Element Call is now built from source**: the call screen (Element Call 0.28.0, the same release Element X bundles) used to come prebuilt from Element's Android package. ElementCtrl now builds it itself from Element Call's published source code, and every release is built that way, so what's inside is exactly what's in the source plus ElementCtrl's changes, which are public in the `element-call` folder of the repository.
- **What was removed from it**, all of it code that calls inside an app never use and that was already switched off in 1.6.0, now left out of the app entirely instead:
  - **Google reCAPTCHA** and Element Call's own **sign-up and account pages**, with their links to Google's privacy policy and terms of service.
  - **PostHog analytics**, including its lists of search engines and web crawlers (Googlebot and others).
  - **Sentry crash reporting**.
  - **Bug-report (rageshake) upload**: nothing in the call screen can send logs anywhere, and requests from other people in a call to send them your logs are ignored.
  - **Background blur**, which needs Google's MediaPipe and was never available on phones anyway.
- **What's still named "Google" in the call screen, and why**: only names, no Google code or services: the message type names of the protocol the LiveKit call service speaks (such as `google.protobuf.Timestamp`), the `x-google-…` settings in WebRTC video connection descriptions, which calls need, and Google in the Matrix library's list of sign-in providers. Nothing in the call screen contacts Google.
- **Checked on every build**: the build fails if any of the removed code comes back, and the call screen is started the way ElementCtrl starts it to check that it loads and tries to reach nothing but your homeserver.
- **Nothing changes in how calls work**: the same call screen, the same calls with Element X and Element Web, the same server setup and the same call service your homeserver announces.
- **README**: the Calls, Privacy, Build from source and License sections say how Element Call is built, which release and commit it's from, and what ElementCtrl changes. The APK carries Element Call's licence (AGPL-3.0) and a note of its source next to it.

## 1.6.0

Voice and video calls, compatible with Element X and Element Web.

### New

- **Voice and video calls** in direct chats and rooms. Tap the phone (voice, in direct chats) or camera (video) button at the top of a chat. Calls use MatrixRTC, the same calls Element X and Element Web make, so you can call anyone on your homeserver who uses them. The call screen is Element Call, the same one Element X bundles (version 0.28.0), shipped inside ElementCtrl and shown in your phone's own WebView: nothing of it is loaded from the web.
- **Join a call that's going on**: a chat with a call in progress shows a **Call in progress** banner with how many people are in it and a **Join** button (and a voice-only join in direct chats). The chat list shows a camera icon next to chats with a call going on.
- **Incoming calls ring**: when someone calls you in a direct chat, ElementCtrl rings on a full screen, also over the phone's lock screen, with **Answer** and **Decline** (in the notification too). It stops ringing when the caller hangs up, when you answer or decline on another of your devices, or after 90 seconds, and then leaves a **Missed call** notification. Declining tells the caller's phone to stop ringing. Ringing comes through the existing push through ntfy, so instant push has to be on. A call started in a group shows as a normal notification ("Started a call").
- **During a call**: mute, camera on or off, switch between the front and back camera, and pick where the sound goes (speaker, earpiece, wired headphones or Bluetooth) on the call screen. The volume buttons control the call, and the screen turns off when you hold the phone to your ear on the earpiece.
- **Picture-in-picture**: going back or home shrinks the call into a small floating window, so you can keep chatting. Closing that window hangs up.
- **Ongoing-call notification** with **Hang up**, which keeps the call going while you use other apps or the screen is off.
- **Settings › Calls**: shows whether your server is set up for calls (and which call service it uses), and on Android 14 and newer whether incoming calls may ring on a full screen, with a shortcut to allow it.
- **Calls aren't set up on your server**: if your homeserver doesn't announce a MatrixRTC (LiveKit) call service, the call button explains that instead of opening a call, with a link to the README's new **Calls** section on what Synapse, LiveKit and lk-jwt-service need.

### Changed

- **The app lock and calls**: answering a call or opening the call screen while ElementCtrl is locked shows the lock screen first; a call that's already running keeps going when the app locks (the connection to your homeserver is only closed once the call has ended); and the call screen never shows in Recents while the app lock is on. With message content hidden in notifications (the app lock's default), the ringing screen and call notifications say "Incoming call" without the caller's name or picture.
- **Permissions**: ElementCtrl can now use the camera and microphone for calls, and asks for them only the first time you start or answer a call. It also declares what Android needs for an ongoing call (a phone-call foreground service, and full-screen ringing). It doesn't get access to your phone calls or contacts.
- **Signing out** now also clears what the call screen stored on the phone.
- **README**: new Calls section (how calls work, the app lock, and the server setup for Synapse, LiveKit and lk-jwt-service), the network destinations for calls in Privacy, and Element Call's licence (AGPL-3.0). Calls are no longer listed under "Not included yet".

### Privacy

- **Calls only go through your own server's call service.** ElementCtrl asks your homeserver which MatrixRTC (LiveKit) service to use and never falls back to a public one. During a call the phone talks to your homeserver, that call service, the LiveKit server it points to, and the TURN/STUN servers that LiveKit server hands out; the call screen refuses every other web address.
- **No analytics or crash reporting in calls.** Element Call's PostHog analytics, Sentry crash reporting, bug-report upload and its fallback STUN server are all switched off, and the call WebView doesn't use Google Safe Browsing or send usage metrics. Element Call's source maps and its MediaPipe background-blur model are left out of the app, so background blur isn't offered in calls.

## 1.5.0

ElementCtrl now has the same layout as the other Ctrl apps. Your chats, encryption and settings are unchanged.

### New

- **Chats and Explore tabs** on a floating glass tab bar at the bottom of the screen, the same frosted island as in the other Ctrl apps. **Explore** is the public room directory that used to be under **New chat › Explore**: search rooms on your server or another one (such as matrix.org) and join them. On tablets and unfolded foldables the tabs are a side rail on the left, next to the chat list and the open chat.
- **Your profile picture is at the top right** of Chats and Explore. Tapping it opens the new **You** page: your picture (tap it to change it), display name, Matrix ID and server at the top, then **Account** (display name, your profile, security and recovery, devices, manage account and the homeserver address), **Settings**, **Sign out**, and **About** with the version underneath.
- **The Ctrl series look**: the same graphite colours, the Manrope typeface, rounded grouped cards, spacing and empty screens as the other Ctrl apps, with pink, the colour of ElementCtrl's icon, as its accent.

### Changed

- **Settings moved to your profile picture › Settings**, in the same grouped cards and order as the other Ctrl apps: **Appearance** (theme, wallpaper colours, chat bubbles), **Notifications**, **Privacy and security** (app lock, fingerprint, lock again, Hide in screenshots, Hide in recent apps), **Chats** (profile pictures, seen by, double-tap to react, room events, Enter sends, link previews), **Ignored people** and **Troubleshooting**. Before, tapping your picture at the top left of Chats opened one long Settings list. The You page is now the only way in.
- **Your account moved to the You page**: display name, your profile, security and recovery, devices, manage account and the homeserver address used to be in Settings › Account; **Sign out** and **About** are on the You page too.
- **Theme, Lock again, notification modes and double-tap reaction** are chosen from a sheet that slides up from the bottom, as in the other Ctrl apps, instead of a dialog.
- **Use wallpaper colours is now off by default**, so ElementCtrl uses its own colours like the other Ctrl apps. If you had turned it on yourself it stays on; otherwise turn it on again in Settings › Appearance if you prefer your wallpaper's colours.
- **The chat list** has a big "Chats" title like the other Ctrl apps' tabs, with search next to your profile picture, and the **New chat** button sits just above the tab bar. Empty screens ("No chats yet", "Pick a chat") use the series style.
- **README**: describes the tabs and the You page, and every settings path now starts with your profile picture.
- Everything else stays as it was: the shared app lock, recents staying blank while locked, Hide in screenshots and Hide in recent apps (both off by default), push through ntfy only with the 15-minute background check, the battery warnings, the two-pane tablet layout, and the chat timeline and composer.

## 1.4.2

### Security

- **With the app lock on, the recent-apps preview always stays blank**, whatever **Hide in recent apps** is set to, so it never shows what's behind the lock. Android takes that preview the moment you leave the app, before it locks.
- **README**: the App lock and privacy text now reads the same as in the other Ctrl apps.

## 1.4.1

### Changed

- **Hide in screenshots** and **Hide in recent apps** are now two separate switches in Settings › Privacy and security, both off by default. **Hide in screenshots** makes screenshots and screen recordings show a blank screen; **Hide in recent apps** makes the recent apps view show a blank card instead of the app, while screenshots still work. If you had turned on the single "Hide from recents & screenshots" (or 1.4.0's "Hide in screenshots and recent apps") switch yourself, both are on after the update.
- **The recent apps view now shows your chats unless you turn on Hide in recent apps.** Before, ElementCtrl always showed a blank card there; now it's your choice, off by default.
- **Always hidden, whatever the switches say**: the lock screen, choosing or changing the app PIN, password sign-in and the recovery key screens, because a password, PIN or key is typed or shown there.
- **README**: describes the two switches.

## 1.4.0

ElementCtrl's app lock is now the one every Ctrl app shares, with its own app PIN that also encrypts your saved sign-in.

### Changed

- **Updating with the app lock on:** the first time you open ElementCtrl, enter your old PIN (or use your fingerprint) one last time, then choose your new app PIN. You can't skip this step. The old PIN's hash and its fingerprint key are deleted as soon as the new PIN is set.
- **The app PIN needs at least 6 digits** (the old lock accepted 4), and very obvious ones like 111111 or 123456 are refused. It's chosen and changed on a full-screen PIN pad instead of a dialog, and turning the lock off asks for it too.
- **Settings > Privacy and security** replaces the App lock section, with the same rows as the other Ctrl apps: **App lock**, **Change app PIN**, **Unlock with fingerprint**, **Lock again** and **Hide in screenshots and recent apps**.
- **Lock again** has the series' choices: as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Your old choice carries over (30 seconds becomes 1 minute). Picking photos or files, sharing, opening a file or link, and the notification and battery settings no longer count as leaving the app, unless you stay away for more than 5 minutes.
- **Unlock with fingerprint** has to be turned on again after you choose the new PIN (**Settings > Privacy and security > Unlock with fingerprint**), because it now unlocks the key that opens your sign-in. The PIN is also needed after the phone restarts and at least every 72 hours.
- **Hide in screenshots and recent apps** now works without the app lock too, and keeps your old setting. The recent-apps screen still never shows your chats.
- **Settings are in the same order as in the other Ctrl apps**: Appearance, Notifications, Privacy and security, then Account, Chats, Ignored people and Troubleshooting, with About last.
- **Pure black theme**: **Settings > Appearance > Theme** now also has Pure black, as in the other Ctrl apps. **Material You colours** is now called **Use wallpaper colours**.
- **Forgot your PIN?** now signs out on this phone and deletes its copy of your chats and keys, even when the app can't reach your homeserver. With recovery set up, your message history comes back after you sign in again.
- **Removed the store listing files** (`fastlane/metadata/android/en-US`) that 1.3.1 added to the repo. Nothing changes in the app.

### Security

- **Your saved sign-in is encrypted with your PIN while ElementCtrl is locked** (with notifications off): the access and refresh tokens and the key to the encrypted message store can only be opened after unlocking, and the connection to your homeserver is closed while the app is locked. The key that decrypts them only exists in memory while the app is unlocked and is wiped the moment it locks.
- **With notifications on**, the background check and push still need your account while ElementCtrl is locked, so your saved sign-in then stays encrypted with the Android Keystore only, as without the app lock. Settings > Privacy and security says so, and message text stays hidden in notifications unless you choose to show it.
- **The PIN can't be guessed off the phone.** It's never stored. It's stretched with 600,000 rounds of PBKDF2 and mixed with a secret held in the phone's secure hardware (StrongBox where available). The old lock only stored a PBKDF2 hash with 120,000 rounds.
- **Wrong PINs lock the screen**: after 5 tries you wait 30 seconds, doubling with every further 5 wrong tries up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't reset the wait.
- **Fingerprint unlock can't be faked**: the fingerprint has to unlock the key that opens your sign-in, so a tampered "success" on a rooted phone doesn't open the app. Adding a new fingerprint turns fingerprint unlock off until you turn it on again with your PIN.
- **The lock and PIN screens are never in screenshots** or screen recordings, whatever "Hide in screenshots and recent apps" is set to.
- **No logs at all in release builds**: error logs, which could name a server or user, are now removed from the app too, like the other logs before.
- **One library fewer**: the app lock no longer needs the AndroidX Biometric (alpha) and Fragment libraries; it uses Android's own fingerprint prompt.

### Fixed

- **README**: describes the new app lock, and the Ctrl series table lists AdCtrl and MemoCtrl.

## 1.3.1

### New

- **Profile pictures in notifications**: message notifications now show the sender's profile picture next to their message, and the chat's picture (the group's picture, or the other person's for a direct chat) as the notification's icon, instead of a letter. This also works when the app hasn't been opened for a long time: pictures come through the Matrix SDK's own encrypted media cache on the phone, so once a picture has been seen it doesn't need the network again. A picture that can't be loaded within a few seconds never holds up the notification; the letter is shown instead.
- The 15-minute background check's notifications (used when push isn't set up) show the chat's picture too.
- Pictures are only shown when message content is shown: with app lock on, or "Show message content in notifications" off, notifications stay as plain "New message" without any picture. They're cleared from memory on sign-out, and nothing new is written to storage.

## 1.3.0

### Changed
- **Push notifications through ntfy only.** Instant push now always comes through [ntfy](https://f-droid.org/packages/io.heckel.ntfy/), and other push apps can no longer be picked. If you used ntfy before, nothing changes: your registration and your homeserver's pusher are kept as they are. If you used another push app, ElementCtrl removes its registration there and moves to ntfy automatically when ntfy is installed; otherwise instant push is off until you install ntfy and turn it on in *Settings → Notifications → Delivery*. The background check keeps running either way.
- **Pushes only go through your own ntfy server.** Your homeserver now always sends pushes to the Matrix push gateway built into your ntfy server (`/_matrix/push/v1/notify`). The public UnifiedPush gateway (`matrix.gateway.unifiedpush.org`) is no longer used as a fallback. If your ntfy server's gateway can't be found, *Settings → Notifications* shows a clear error (with a **Try again** button) instead of quietly sending pushes through another server, and a pusher left over from the public gateway is removed from your homeserver.
- **The 15-minute background check now always runs**, also when push through ntfy is on. If a push never arrives (ntfy lost its connection, or your homeserver dropped the pusher), the message still turns into a notification within about 15 minutes. Messages that already showed a notification from a push aren't shown a second time.
- **The Notifications settings warn clearly when something will stop notifications**, each with buttons to fix it:
  - **ntfy isn't installed**, with an **Install ntfy** button that opens ntfy on F-Droid. This is checked again every time you come back to the screen, so it disappears as soon as ntfy is installed.
  - **ElementCtrl is battery-restricted** (battery optimisation on, or battery usage set to Restricted), with **Allow** and **App info** buttons.
  - **ntfy is battery-restricted**: Android can then cut ntfy's connection to your ntfy server while the phone sleeps. With **ntfy's App info** (to set its battery usage to Unrestricted) and **Open ntfy** buttons.
  - **Instant push isn't working**, with the reason in full (for example "ntfy didn't answer" or a missing gateway) and a **Try again** button.
- **The "Notifications may be missed" banner** in the chat list now also appears when ntfy is battery-optimised, and its button takes you to ntfy's battery setting.
- **ntfy temporarily unavailable**: when ntfy reports that your ntfy server is down, *Delivery* now says so. Push picks up again by itself once the server is back, and the background check keeps running in the meantime.

### Fixed
- **Messages that sometimes didn't arrive as notifications.** A message pushed while ElementCtrl was still catching up on an earlier one could wait up to half a minute, plus a growing back-off delay, before it was fetched; it is now fetched right away. A message that can't be fetched yet (no network, homeserver not answering) is now tried again within 20 to 60 seconds in a separate job, so new pushes never wait behind it, and after three tries it still shows a "New message" notification instead of nothing.
- **Push setup no longer hangs on "Setting up push…"** when ntfy doesn't answer: after 30 seconds it says so and asks you to open ntfy once.

### Privacy
- **De-Googled.** The push library ElementCtrl used is gone, together with the Google crypto library (Tink) it brought along and its built-in check for Google Play services. ElementCtrl now talks to ntfy directly, contains no Google libraries or services and never contacts Google. The "ntfy on Play" button and every other mention of Google in the app are gone; ntfy is only offered from F-Droid.
- **README**: the Google-free line now reads "Google-free and de-Googled", push is described as ntfy-only with the background check as a fallback, and the setup steps are updated.

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
