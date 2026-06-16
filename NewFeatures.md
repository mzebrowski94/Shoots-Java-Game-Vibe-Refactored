# New Features Plan (post-refactor)

> Companion to `RefactorPlan.md`. The refactor restructures *existing* behaviour; this file tracks
> *new* gameplay/engine features added on top of the refactored model. Same conventions: each
> top-level item is a *cluster* of 1-2 tightly-coupled sub-tasks; work top to bottom; a cluster is
> `[x]` only once every sub-item under it is `[x]`.
>
> Before starting a feature, read `STATE.md` for the current package map and established contracts,
> and reuse them rather than redesigning. Build/verify with `./mvnw test` (vendored toolchain in
> `tools/`; see `tools/AgentWorkflow.md`).

## [ ] A. Audio (SFX + music)
> Moved here from `RefactorPlan.md` (was the audio half of the old "Audio & Final Integration"
> cluster) — sound is a new feature on top of the refactored engine, not part of the migration.
- [ ] Implement a `SoundManager` with a small `javax.sound.sampled` clip pool so overlapping SFX
      (shots, hits/captures, explosions) don't cut each other off or stutter. Load clips once and
      reuse; fail soft (log + continue) when a sound asset is missing. Wire SFX triggers into the
      existing decoupled event hooks (e.g. `DiscSystem.DiscEventSink` capture-hit / disc-retire) so
      audio stays decoupled from physics and rendering.

## [x] B. CONTROLS menu screen
> Adds a new selectable menu option that shows the key bindings for all four players. Self-contained
> in the AWT menu shell (`...ui.GameMenu`); no game-state or simulation changes.
- [x] Add a `CONTROLS` option to `MenuEnum` and place it in the menu just above `QUIT`
      (Continue / Start New Game / Player Number / Round Limit / Round Time / Controls / Quit). Wired into
      `GameMenu`'s up/down navigation, `selectedRow()` highlight, and `drawChoosenMenuOption`.
- [x] Implement a controls overlay sub-screen in `GameMenu`: selecting `CONTROLS` (CONFIRM) sets
      `showingControls` and `drawControls` shows a panel listing each player's rotate-left / rotate-right /
      shoot keys (P1 arrows, P2 WASD, P3 numpad, P4 IJL) on the menu backdrop; while open it swallows menu
      input and CONFIRM or ESC returns. Keys are read live from `InputBridge.keyNameFor(GameAction)` so the
      screen stays truthful to the actual bindings. Tests: `GameMenuControlsTest` (open / return-on-ENTER /
      return-on-ESC / navigation-swallowed).

> **Bundled fix (no separate cluster):** on pause of an in-progress game the highlighted menu option now
> defaults to `CONTINUE` instead of `START NEW GAME`. `GameMenu.selectContinue()` is called from
> `PausedState.enter()`, guarded so it only applies mid-game (`actualRoundNumber != 0 && !gameEnd`); before
> the first round and on the game-over screen the default is left unchanged (CONTINUE is unavailable there).
