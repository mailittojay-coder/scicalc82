# SciCalc 82

A Casio fx-82 style scientific calculator for Android (Kotlin + Jetpack Compose, Android 8.0+).
It is an independent app with its own name, colours and icon; it is not affiliated with Casio.

## Build and run

1. Open this folder in **Android Studio** (Ladybug or newer) and let Gradle sync.
2. Plug in a phone (USB debugging on) or start an emulator, then press **Run**.
3. For an installable APK: **Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)**,
   or run `./gradlew assembleDebug`. The file lands in `app/build/outputs/apk/debug/`.

### Build the APK on GitHub (no Android Studio needed)

1. Create a new GitHub repository and push this folder to it (branch `main`).
2. Open the **Actions** tab. The *Build APK* workflow runs on every push; you can also run it by hand with **Run workflow**.
3. When it finishes, open the run and download **SciCalc82-debug-apk** from *Artifacts*. Unzip it and copy `app-debug.apk` to your phone.
4. On the phone, open the file and allow "install unknown apps" for your file manager or browser when asked.

The debug APK is signed with a debug key, which is fine for personal use. Publishing to the Play Store needs a release build signed with your own keystore.

To run the calculation tests: `./gradlew testDebugUnitTest`.

## Using it

Like the real thing: press **SHIFT** (gold) then a key for the gold function above it, **ALPHA** (red)
for the red letter. `STO` is SHIFT + `RCL`; then press a lettered key (A–F, X, Y, M).

| Feature | How |
|---|---|
| Expression entry with cursor | ◀ ▶ move, DEL deletes, SHIFT+DEL toggles insert/overwrite |
| Replay | ▲ ▼ step through earlier calculations |
| Ans | `Ans` key; typing an operator after a result continues from it |
| Fractions | `a b/c` key; `S⇔D` toggles fraction/decimal; SHIFT+`a b/c` switches mixed/improper |
| Degrees-minutes-seconds | `°′″` key (entry, and converts a result) |
| Angle units | MODE menu, or SHIFT+`Ans` (DRG▸) cycles Deg/Rad/Gra |
| Display | MODE: Norm 1, Norm 2, Fix, Sci |
| Trig / inverse / hyperbolic | `sin` `cos` `tan`, SHIFT for inverse, `hyp` first for hyperbolic |
| Powers and roots | `x²` `x³` `^` `√` SHIFT+`^` (x-th root) SHIFT+`x³` (cube root) |
| Logs | `log` `ln`, SHIFT for 10ˣ and eˣ |
| Combinatorics | `nCr`, SHIFT+`nCr` = nPr, SHIFT+`x⁻¹` = x! |
| Polar/rectangular | `Pol(x , y)` and SHIFT+`Pol(` = `Rec(r , θ)`; SHIFT+`)` types the comma |
| Random | SHIFT+`×10ˣ` = Ran#, SHIFT+`.` = RanInt( |
| Memory | `M+`, SHIFT+`M+` = M−, variables A–F, X, Y, M kept between launches |
| Reset memory and history | SHIFT+AC (CLR) |

Errors show `Math ERROR` or `Syntax ERROR`; press ◀ to go back and edit.

## Layout

- `core/Engine.kt` – parser and evaluator (precedence, implicit multiplication, trig modes)
- `core/Format.kt` – Norm/Fix/Sci formatting, fractions, DMS
- `core/Calculator.kt` – key handling, cursor, history, memory
- `core/Keys.kt` – the keypad: every key with its SHIFT and ALPHA function
- `ui/` – Compose LCD, keypad and MODE dialog
- `src/test/` – 27 tests that press real keys
