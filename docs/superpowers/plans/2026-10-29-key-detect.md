# Detect key and every-key lists Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Detect key" button on every key row and in the bind form, working from the game (default) or from a capture screen, plus an optional "every key" list in the Hold/Key dropdowns.

**Architecture:** Application layer gets a `KeyDetector` (one pending detection per player, 10 s timeout) fed either by the existing `PlayerInput` pipeline (`KeyDispatcher.pressed` asks the detector first) or by a new `KeyCapture` port whose Hytale adapter is a CustomUI page catching `KeyDown`. The panel page hands itself to the detector, closes (game mode) or is replaced by the capture page (screen mode), and is re-opened as the **same `ControlPanelPage` instance** when the result arrives, so tab, keymap mode, search and form contents survive.

**Tech Stack:** Java 25, Hytale server 0.7.4 CustomUI, JUnit 5 + AssertJ, Gradle (`-Pstrict`).

**Spec:** `docs/superpowers/specs/2026-10-29-key-detect-design.md`

## Global Constraints

- Settings: `controlcore:key-detect-mode` choice `game` | `screen`, default `game`; `controlcore:all-keys-in-lists` bool, default `false`; both in category `controlcore.settings.category.keymaps` ("Keymaps").
- Detect timeout: 10 s. Capture screen: Esc cancels.
- New `Key` constants are public API → API version 2.3.0 (docs/api.md, CHANGELOG, `api_version` wherever 2.2.0 is declared).
- Every `.ui` element id unique (LayoutIdsTest); `.ui` translation keys only letters, digits, dots.
- All new text in en-US, pt-BR, es-ES `controlcore.lang` (LanguageFilesTest).
- No code comments outside public-API Javadoc; new `Key` constants get Javadoc in the existing style.
- Capture screen KeyDown payload is `{"Source":"key","Keycode":<int>,"Repeat":<bool>}` (SDL keycodes: letters/digits/punctuation = ASCII, F1–F12 = 1073741882–1073741893, numpad / * - + Enter 1–9 0 . = 1073741908–1073741923, LCtrl/LShift/LAlt/LGui = 1073742048–1073742051, RCtrl/RShift/RAlt/RGui = 1073742052–1073742055, arrows Right/Left/Down/Up = 1073741903–1073741906, Insert/Home/PgUp = 1073741897–1073741899, End/PgDn = 1073741901–1073741902, Delete = 127, CapsLock = 1073741881, PrintScreen/ScrollLock/Pause = 1073741894–1073741896, NumLock = 1073741907, Menu(Application) = 1073741925, Esc = 27, Tab = 9, Enter = 13, Backspace = 8, Space = 32).

## Review Focus

1. Player leaves, dies, or opens another screen while a detection is pending → detection is dropped, nothing reopens, no leak (test in Task 3).
2. Detect pressed twice / on two rows quickly → only the latest detection is pending; the first is cancelled silently (test in Task 3).
3. A non-admin presses Detect on a Server defaults row (forged event) → refused like a normal save (test in Task 5).
4. Detected key is a modifier alone (Ctrl) or a hotbar key → modifier alone is ignored (keep waiting); hotbar is accepted as `1`–`9` (tests in Tasks 3 and 4).
5. Capture screen receives `Repeat:true` events → repeats of the main key are ignored; repeats of a modifier keep it "held" (test in Task 4).

---

### Task 1: New keys in the `Key` API

**Files:**
- Modify: `src/main/java/net/ofatech/controlcore/domain/model/keys/Key.java`
- Modify: `docs/api.md`, `CHANGELOG.md` (Unreleased), wherever API `2.2.0` is declared (`git grep -n "2\.2\.0"`)
- Test: `src/test/java/net/ofatech/controlcore/domain/model/keys/KeyComboTest.java` (create if missing, else extend)

**Interfaces:**
- Produces: `Key.NUMPAD_0 … NUMPAD_9, NUMPAD_DIVIDE, NUMPAD_MULTIPLY, NUMPAD_MINUS, NUMPAD_PLUS, NUMPAD_ENTER, NUMPAD_PERIOD, NUM_LOCK, PRINT_SCREEN, SCROLL_LOCK, PAUSE, MENU` — none has a signal; labels `"NUMPAD 0"…"NUMPAD 9"`, `"NUMPAD /"`, `"NUMPAD *"`, `"NUMPAD -"`, `"NUMPAD +"`, `"NUMPAD ENTER"`, `"NUMPAD ."`, `"NUM LOCK"`, `"PRINT SCREEN"`, `"SCROLL LOCK"`, `"PAUSE"`, `"MENU"`.

- [ ] **Step 1: Failing tests** `numpadKeysParseAndPrint`: `KeyCombo.parse("ctrl+numpad_5")` succeeds and `toString()` is `"CTRL + NUMPAD 5"`; `KeyCombo.parse("NUMPAD 5")` also succeeds; `KeyCombo.parse("numpad_5").value().trigger()` fails with code `Keys.NotDetectable`.
- [ ] **Step 2:** Run `.\gradlew.bat test --tests "*KeyComboTest"` → FAIL (unknown key).
- [ ] **Step 3:** Add the constants after `SLASH` and before `MOUSE_LEFT`; extend `KeyCombo` aliases only if parse by enum name / label doesn't already cover them.
- [ ] **Step 4:** Run the test → PASS; run `javadoc -Xdoclint:all,-html` check used by the project (or `clean build -Pstrict`) → 0 warnings.
- [ ] **Step 5:** Bump API to 2.3.0 in docs/changelog; commit `Keys: numpad and other extra keys (API 2.3.0)`.

### Task 2: The two settings

**Files:**
- Create: `src/main/java/net/ofatech/controlcore/application/keymaps/KeyDetectSettings.java` (`@AutoService(FeatureModule.class)`, pattern of `KeepGameActions.java`)
- Modify: the three `controlcore.lang` files
- Test: `src/test/java/net/ofatech/controlcore/application/keymaps/KeyDetectSettingsTest.java`

**Interfaces:**
- Produces: `enum DetectMode { GAME, SCREEN }`; `KeyDetectSettings.mode(): DetectMode`; `KeyDetectSettings.allKeys(): boolean`. Registered as a service other modules can obtain the same way `KeepGameActions` exposes its `Setting`.

- [ ] **Step 1: Failing test** `defaultsAreFromTheGameAndShortLists`: after runtime start, `/cc-get controlcore:key-detect-mode` → `game`; `controlcore:all-keys-in-lists` → `false`; `/cc-set controlcore:key-detect-mode screen` then `mode() == SCREEN`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Register `SettingDefinition.server("controlcore","key-detect-mode",SettingType.choice("game","screen"),"game")` and `server("controlcore","all-keys-in-lists",SettingType.bool(),false)`, both `.inCategory(Text.key("controlcore.settings.category.keymaps","Keymaps"))`, with names "How Detect key works" / "Show every keyboard key in key lists" and short descriptions (≤ one line at 1320 px).
- [ ] **Step 4:** Run → PASS. **Step 5:** Commit `Keymaps: Detect key and every-key settings`.

### Task 3: `KeyDetector` (application)

**Files:**
- Create: `src/main/java/net/ofatech/controlcore/application/keymaps/KeyDetector.java`
- Modify: `application/keymaps/KeyDispatcher.java` (ask detector first in `pressed`), `application/keymaps/KeymapsModule.java` (wire it), player-leave handler (drop pending)
- Test: `src/test/java/net/ofatech/controlcore/application/keymaps/KeyDetectorTest.java`

**Interfaces:**
- Produces:
  - `record Detected(String keys)` — keys in `KeyCombo` text form, e.g. `"CTRL+Q"`, `"NUMPAD_5"`.
  - `void KeyDetector.fromGame(PlayerId player, Consumer<Optional<Detected>> done)` — replaces any pending detection of that player (old one gets nothing), schedules a 10 s timeout that calls `done(Optional.empty())`.
  - `void KeyDetector.offer(PlayerId player, Optional<Detected> result)` — used by the capture screen (Task 4) to complete a pending detection.
  - `boolean KeyDetector.capture(PlayerId player, KeySignal pressed, Set<KeySignal> held)` — returns true (consume/cancel) when a game detection was pending.
  - `void KeyDetector.cancel(PlayerId player)`.
- Mapping: press signal → `KeyPicker`-compatible key (`ABILITY_1`→`Q`, `ABILITY_2`→`E`, `ABILITY_3`→`R`, `ABILITY_4`→`ABILITY_4`, `USE`→`F`, `GAME_MODE_SWAP`→`O`, `DODGE`→`DODGE`, `WALK_TAP`→`ALT`, `PRIMARY/SECONDARY/PICK`→`MOUSE_LEFT/RIGHT/MIDDLE`, `HOTBAR_n`→`n`); hold from `held`: CROUCHING→CTRL, WALKING→ALT, SPRINTING→SHIFT, JUMPING→SPACE (first one in that order; MOVING/STANDING_STILL ignored).

- [ ] **Step 1: Failing tests:** `detectsTheNextPressWithItsHold` (hold CROUCHING, press ABILITY_1 → `"CTRL+Q"`, `pressed` returns true, no bind ran); `timesOutAfterTenSeconds` (FakeScheduler run after 10 s → `Optional.empty()`); `aSecondDetectReplacesTheFirst`; `leavingDropsTheDetection` (no callback after leave); `hotbarIsDetectedAsADigit` (HOTBAR_3 → `"3"`); `withoutDetectionNormalBindsStillRun`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement (ConcurrentHashMap of pending per player; timeout via `Scheduler.runLater`). **Step 4:** PASS. **Step 5:** Commit `Keymaps: KeyDetector reads the next key a player presses`.

### Task 4: Capture screen (Hytale)

**Files:**
- Create: `src/main/java/net/ofatech/controlcore/domain/ports/game/KeyCapture.java` — `void open(PlayerId player, Consumer<Optional<String>> done)`
- Create: `src/main/java/net/ofatech/controlcore/infrastructure/platforms/hytale/input/SdlKeys.java` — `static Optional<Key> key(int keycode)`, `static boolean isModifier(int keycode)`, `static Optional<String> hold(int keycode)` (`CTRL`/`ALT`/`SHIFT`)
- Create: `.../infrastructure/platforms/hytale/input/KeyCapturePage.java` + `src/main/resources/Common/UI/Custom/Pages/ControlCore/KeyCapture.ui` (title "Press a key…", hint "Esc cancels", focused `#CaptureInput` TextField with `KeyDown` binding; codec decodes `Keycode` int and `Repeat` bool)
- Create: `.../input/HytaleKeyCapture.java` (opens the page on the world thread; implements the port)
- Modify: platform wiring (`HytalePlatform`), `FakePlatform` (fake `KeyCapture` with `List<Consumer<Optional<String>>> opened`)
- Test: `src/test/java/net/ofatech/controlcore/infrastructure/platforms/hytale/input/SdlKeysTest.java`, `KeyCaptureLogicTest.java`

**Interfaces:**
- Consumes: Task 1 keys, Task 3 `KeyDetector.offer`.
- Produces: capture result text `"[HOLD+]KEY"` where KEY is the `Key` enum name (e.g. `"CTRL+NUMPAD_5"`).
- Page logic (pure, testable as `KeyCaptureLogic.accept(int keycode, boolean repeat, long nowMillis): Optional<Optional<String>>`): Esc → `Optional.of(Optional.empty())` (cancelled); modifier (incl. repeats) → remember `heldUntil = now + 600`, return empty (keep waiting); non-modifier repeat → ignore; non-modifier → result with hold if `now <= heldUntil`; unknown keycode → keep waiting.

- [ ] **Step 1: Failing tests:** `SdlKeysTest.mapsEveryRange` (97→A, 49→NUM_1, 1073741882→F1, 1073741917→NUMPAD_5, 1073741912→NUMPAD_ENTER, 1073741906→UP, 127→DELETE, 1073742048→modifier CTRL, 1073742054→modifier ALT); `KeyCaptureLogicTest`: `ctrlThenNumpadFive` → `"CTRL+NUMPAD_5"`; `staleModifierIsNotHeld` (Ctrl at t=0, Numpad5 at t=2000 → `"NUMPAD_5"`); `escapeCancels`; `repeatsOfTheMainKeyAreIgnored`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** PASS; `clean build -Pstrict` (LayoutIdsTest, lang tests). **Step 5:** Commit `Keymaps: capture screen that reads any key`.

### Task 5: Detect in the panel

**Files:**
- Modify: `SettingKeyRow.ui` (add `$C.@SmallTertiaryTextButton #Detect` after `#Key`), `ControlPanel.ui` (bind form: `#NewBindDetect` after `#NewBindKey`; Keymaps tip label `#KeymapTip`)
- Modify: `ui/SettingRows.java` (bind `DETECT = "DetectKey"` event with row id), `ui/KeyBindForm.java` (bind `#NewBindDetect`; keep a `formKeys` string so a detected custom combo survives `compose` — fixes the "CUSTOM becomes none" caveat), `ui/ControlPanelPage.java` (handle DETECT), `ui/KeyPicker.java` (Task 6 consumes)
- Modify: `HytaleControlPanelView` / `ControlPanelPage` — add `void reopen()` that opens **this same page instance** for its player on the world thread (`getPageManager().openCustomPage(entity, store, this)`), skipping if the player is gone.
- Test: `src/test/java/net/ofatech/controlcore/application/runtime/KeyDetectPanelTest.java` (runtime-level through `FakePlatform`; UI classes are not unit-tested today, so test through the editor/save path the panel calls)

**Interfaces:**
- Consumes: `KeyDetector.fromGame/offer/cancel`, `KeyCapture.open`, `KeyDetectSettings.mode()`.
- Behaviour on DETECT (row `id` or form):
  1. Check the same edit right a save would need (Server defaults rows: admin / keys right; My keys: own). Refuse → footer error, panel stays.
  2. GAME: `detector.fromGame(viewer, result -> later(() -> apply(result)))`, send toast `controlcore.keys.detect_toast` ("Press the key now… (10 s)"), close the page. SCREEN: `capture.open(viewer, text -> detector.offer(viewer, text.map(Detected::new)))` after registering the same callback with the detector (`fromGame` registration must not consume game presses in SCREEN mode — add `KeyDetector.fromScreen(PlayerId, Consumer<Optional<Detected>>)` that only waits for `offer`).
  3. `apply(result)`: empty → `reopen()` with footer `controlcore.keys.detect_none` ("No key detected. Extra keys need a game action in Hytale's Controls."); row → `section.save(id, KeyPicker.compose(hold, key, current))` exactly like a pick, then `reopen()` showing the saved row; form → set form Hold/Key (or `formKeys` when not representable), `reopen()`.
- Tip label text `controlcore.ui.keymaps.tip`: "Extra keys need a game action in Hytale's Controls. Detect key reads the key you press."

- [ ] **Step 1: Failing tests:** `detectOnMyKeysSavesTheDetectedCombo` (press Detect via the panel action path → FakePlayerInput press with CROUCHING held + ABILITY_3 → My keys value `CTRL+R`, panel reopened once); `detectOnServerDefaultsIsRefusedForPlayers`; `detectInTheFormFillsButDoesNotSave`; `screenModeUsesTheCaptureScreen` (setting screen → FakeKeyCapture opened; completing with `"NUMPAD_5"` saves `NUMPAD_5` with a not-detectable warning in the row description); `timeoutReopensWithAHint`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** PASS + `clean build -Pstrict`. **Step 5:** Commit `Keymaps: Detect key on rows and in the bind form`.

### Task 6: Every key in the lists

**Files:**
- Modify: `ui/KeyPicker.java`, its callers (`SettingRows.showKeys`, `KeyBindForm`), lang files
- Test: `src/test/java/net/ofatech/controlcore/infrastructure/platforms/hytale/ui/KeyPickerTest.java`

**Interfaces:**
- Produces: `KeyPicker.keys(Translator tr, Picked picked, boolean allKeys)` and `KeyPicker.read(String text, boolean allKeys)`; the old 2-arg overloads stay as `allKeys=false`.
- With `allKeys`: after the existing `KEYS`, append every `Key` not already represented and not a modifier (`CTRL`, `ALT`, `SHIFT`, `SPACE` stay Hold-only; mouse 4/5 included), in enum order, value = enum name, label = `key.label()` plus `controlcore.panel.keypick.unheard` (" (server can't hear it)") when `key.signal(false)` is empty. `read` treats such single-key binds (with an optional supported hold) as representable instead of Custom.

- [ ] **Step 1: Failing tests:** `shortListByDefault` (no `NUMPAD_5` option); `allKeysAddsNumpadWithAWarning` (option `NUMPAD_5` labelled `"NUMPAD 5 (server can't hear it)"`, `Q` keeps its normal label, no `CTRL` in Key list); `allKeysReadsNumpadBindsAsPicks` (`read("CTRL+NUMPAD_5", true)` → hold `CTRL`, key `NUMPAD_5`, not custom); `composeKeepsCustomInTheForm`.
- [ ] **Step 1b:** Extend the existing warning (spec goal 4): change `controlcore.errors.keys.not_detectable` to "Hytale does not tell servers about {missing} yet. Give it a game action in Hytale's Controls and use Detect key." (all three languages); add a test asserting `KeymapsText.problems` for `NUMPAD_5` contains "Detect key".
- [ ] **Step 2:** FAIL. **Step 3:** Implement and pass `KeyDetectSettings.allKeys()` to the two callers. **Step 4:** PASS + `clean build -Pstrict`. **Step 5:** Commit `Keymaps: every keyboard key in the lists (optional)`.

### Task 7: Docs and in-game check

**Files:** `wiki/Key-Binds.md`, `wiki/For-Players.md`, `CHANGELOG.md`, `docs/api.md`

- [ ] **Step 1:** Wiki: Detect key (both modes), the two settings, the "give extra keys a game action" tip with the LoneStar numpad example.
- [ ] **Step 2:** `clean build -Pstrict`; dev-server console smoke: `/cc-get controlcore:key-detect-mode`.
- [ ] **Step 3:** Commit `Docs: Detect key`; hand the owner the in-game checklist (row Detect from the game with Ctrl+R, form Detect, screen mode with Numpad 5, every-key list fit in Portuguese, timeout hint).
