# Google Play listing – Animal Tap

Everything needed for the Play Console, in the order the Console asks for it.

## Files to upload
| What | File |
|---|---|
| App bundle (Production / Testing → Create release) | `apk/AnimalTap.aab`. Build it with `build-apk.bat`, or download the `AnimalTap.aab` artifact from GitHub Actions |
| App icon 512×512 | `store/icon-512.png` |
| Feature graphic 1024×500 | `store/feature-graphic-1024x500.png` |
| Phone screenshots (2–8) | `store/screenshots/screenshot1.png` … `screenshot5.png` (1080×2160) |
| Privacy policy | `store/privacy-policy.html`. Host it at a public URL and paste that URL into the Console (see below) |

Regenerate the icon and feature graphic with `python tools/make_store_graphics.py`.

## Store listing text
**App name** (max 30): `Animal Tap`

**Short description** (max 80):
```
Tap cute animals to hear their sounds! 15 animals & 6 colorful themes for kids.
```

**Full description** (max 4000):
```
Animal Tap is a happy, simple first game for little ones. Tap an animal to hear its sound,
watch it jump, wiggle or spin, and see a speech bubble pop up!

🐄 15 friendly animals: cow, dog, cat, duck, sheep, pig, lion, bird, frog, leopard,
   elephant, rhino, monkey, eagle and tiger
🔊 A fun sound for every animal: Moo! Woof! Roar! Pawoo!
🎉 Bouncy animations, music notes and stars on every tap
🎨 6 colorful themes: Meadow, Ocean, Sunset, Night, Candy and Jungle
👶 Big buttons made for small fingers, with nothing to read

Safe for kids:
✔ No ads
✔ No in-app purchases
✔ No internet needed – works offline
✔ No data collected, no permissions

Great for toddlers and preschoolers learning animal names and sounds.
```

**App category:** Games → Educational (or Apps → Education)
**Tags:** Educational, Kids, Animals
**Contact email:** your developer email (required, shown publicly)

## App content forms (Policy → App content)
- **Privacy policy:** the public URL of `privacy-policy.html` (replace `YOUR_EMAIL_HERE` in it first).
- **Ads:** No, the app does not contain ads.
- **App access:** All functionality is available without special access.
- **Content rating (IARC questionnaire):** Category *Game*. Answer **No** to violence, fear, sexuality,
  language, controlled substances, gambling, user interaction/chat, sharing location, and digital
  purchases. Expected result: Everyone / PEGI 3.
- **Target audience and content:** age groups *5 and under* and *6–8* (optionally *9–12*).
  Because this includes children, the app enrolls in the **Families policy**. Answer that the app
  does not use ads SDKs, does not collect data, and has no unintended appeal concerns.
- **Data safety:**
  - Does your app collect or share any of the required user data types? → **No**
  - Result shown on the store: "No data collected" and "No data shared with third parties".
- **Government app:** No. **Financial features:** None. **Health:** None. **News app:** No.

## Release notes (version 1.1)
```
• New home screen with Play and Exit buttons
• 6 colorful themes to choose from
• 6 new animals: leopard, elephant, rhino, monkey, eagle and tiger
```
