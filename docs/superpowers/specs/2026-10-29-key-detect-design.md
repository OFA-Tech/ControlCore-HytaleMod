# Detect key and every-key lists (design)

Date: 2026-10-29 · Target: 0.19.0 (branch `feature/players`) · Requested by the owner after LoneStar_144 asked for numpad binds.

## Intent

Players want to bind keys the dropdowns don't offer (numpad, F-keys, …) and want an easy way to pick "the key I
actually press" without knowing which game action it is mapped to. Hytale currently only tells the server the
*game action* a key triggers during play (Ability 1, Use, hotbar 3…), never the key itself; Hypixel plans to
expose the whole keyboard later. ControlCore should make today's workaround easy and be ready for that future
with two switches the server owner controls.

## Goals

1. **Detect key** button in the Keymaps bind form (+ New bind / Edit) and on every key row (Server defaults and
   My keys, admin panel and `/prefs`). Pressing it fills that row's Hold + Key with the key the server identified.
2. Detect works in one of two ways, chosen by a ControlCore server setting:
   - **From the game** (default): the panel closes, a toast says "Press the key now…", the next key action the
     server receives from that player (within 10 s) is taken, the panel reopens with it filled in. Only keys the
     server can hear are detected; the toast/tip tells players to give extra keys (numpad) a game action in
     Hytale's Settings → Controls first.
   - **From a capture screen**: a small ControlCore window opens with a focused field; the next key pressed there
     (any keyboard key, Numpad 5 included, plus held Ctrl/Alt/Shift) is taken and the panel reopens with it.
3. **Every key in key lists**: a ControlCore server setting (default off). When on, the Key dropdowns also list
   every key of a US international keyboard (letters, digits, F1–F12, numpad 0–9 and / * - + . Enter, arrows,
   Insert/Delete/Home/End/Page Up/Page Down, Esc, Tab, Caps Lock, Enter, Backspace, punctuation, Print Screen,
   Scroll Lock, Pause, Num Lock, Menu, Mouse 4/5). Keys the server can't hear during play are labelled
   "(server can't hear it)".
4. A bind saved on a key the server can't hear shows a warning on its row (and in `/cc-keybind-add` / `/cc-bind`
   replies): "The server can't hear this key during play. Give it a game action in Hytale's Controls and use
   Detect key."
5. A short tip line in Keymaps explaining the above in one sentence.

## Non-goals

- Making keys the server can't hear actually fire today (impossible on Hytale 0.7.x).
- Reading players' client key settings (not possible).
- A leader-key system (rejected earlier by the owner).

## Anti-goals

- A bind that silently never works without any warning.
- Detect that leaves the player stuck (no timeout / panel never comes back).
- Swallowing the player's normal game action while detecting "from the game" in a way they notice
  (detecting is allowed to cancel that one press, since the panel was just closed for it).

## Constraints

- Hytale CustomUI: every element id unique per layout (LayoutIdsTest); `.ui` translation keys letters/digits/dots.
- Key capture screen: CustomUI `KeyDown` on a focused TextField reports SDL keycodes (letters = ASCII, F1 =
  1073741882, numpad 1073741908–1073741923, modifiers 1073742048–1073742055) — already proven by `/cc-key-probe`.
- Input from the game comes through `InboundPacketHooks` / the existing key-action pipeline (works in singleplayer
  and dedicated since 877a58c).
- `Key` is public API: new keys (numpad, Print Screen, …) are an additive API change → API 2.3.0.
- Permissions: Detect on Server defaults rows and the bind form needs the same rights as editing them today;
  on My keys it's the player's own.
- All new text translated en-US / pt-BR / es-ES. No code comments outside API Javadoc.

## Approaches considered

1. **From the game only** — always works for binds that fire; can't see unmapped keys.
2. **Capture screen only** — sees every key, but most detected keys never fire today.
3. **Both, chosen by a setting (picked by the owner)** — default "from the game" because those binds work today;
   switch to the capture screen when Hytale opens up the whole keyboard (or for owners who want it now).

## Decided

- Settings (ControlCore, Mod Settings → ControlCore: Keymaps):
  - `controlcore:key-detect-mode` choice `game` | `screen`, default `game`.
  - `controlcore:all-keys-in-lists` bool, default `false`.
- Detect available both in the admin panel and in `/prefs` (My keys).
- Default dropdown contents stay exactly as today when `all-keys-in-lists` is off.

## Left to the builder

- Exact layout of the Detect button in rows (must fit at 1320 px and in Portuguese) and toast wording.
- How a detected hold + key maps onto the existing Hold/Key dropdowns (e.g. Ctrl held + Numpad 5 → Hold Ctrl,
  Key Numpad 5); a detected key outside the current list when `all-keys-in-lists` is off is shown as
  "Custom: …" like today's unrepresentable binds.
- Cancelling a detect (timeout 10 s, Esc in the capture screen, or opening another screen) and reopening the
  panel on the same tab, view, search and form contents.
