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

## [X] C. AI players (computer-controlled opponents)
> Adds 0-4 computer-controlled players, selectable from the menu, in a new `...ai` package. An AI is
> **just another input source**: it drives the same `PlayWorld.applyInput(playerId, AimInput, shoot)` /
> `fire(playerId)` API a human uses, so there is no special-case physics and nothing in the hot disc/
> collision loop changes. Targeting reuses the existing alloc-free `LaserPredictor` (scan candidate
> firing angles, see which capture-point tiles a bounce path reaches, score them) — utility-style
> "score each option, pick the best", which fits this one-verb (aim+shoot) game far better than an FSM
> or behaviour tree and is the cheapest to run.
>
> **Design decisions locked (discussion 2026-06):**
> - **Decision model:** new world-aware `PlayerAiController` interface in `...ai`; the existing
>   entity-level `entity.AiStrategy` contract is left untouched (STATE.md: don't redesign contracts).
> - **Targeting cost:** coarse candidate-angle scan (configurable, ~16-32 angles), **amortized across
>   frames** and **round-robin staggered across AIs** so the four AIs never all re-evaluate on the same
>   tick. Combined with a small per-AI per-frame scan budget — the "golden middle" throttle: flat
>   worst-case frame cost, HARD AI still reacts promptly. Best target is cached between re-evaluations.
> - **Seed / determinism:** difficulty sets each knob's base value; the existing `PlayWorld` seed drives
>   a deterministic RNG stream so every miss, decision delay, and target choice is **reproducible for a
>   given seed + difficulty** (foundation for future round replay).
> - **Difficulties:** `RANDOM, EASY, NORMAL, HARD, VERY_HARD`. `RANDOM` = each knob randomized within a
>   wide band, drawn deterministically from the seed (unpredictable but reproducible). EASY..VERY_HARD
>   scale the knob bases monotonically, each with a small seed-derived per-AI deviation so sibling AIs
>   of the same level differ slightly.
> - **Slot assignment:** humans fill the low player slots, AI fills the rest; AI count capped at
>   `maxPlayers - humans`. Difficulty is fixed at match start.
>
> **Skill knobs (all cheap to evaluate):** hit/miss accuracy (angle perturbation before firing),
> cursor speed (rotation steps/frame, via `AimController` clamping), max discs in flight (existing
> `DiscAttackStrategy` cap) + max discs per shot/volley (new tunable), retake stubbornness, defend-owned
> tendency (react when an owned capture point is being eroded — read from `CaptureScoring`), bounce-path-
> length preference (short/direct vs. long trick shots), reaction/decision interval, target-selection
> mode (nearest-reachable / highest-value / contested), and volley pacing (cooldown between shots).
> **Hard constraint, not a knob:** never fire at flanking blocks — filtered out of candidate angles.
>
> Build/verify with `./mvnw test` (vendored toolchain; see `tools/AgentWorkflow.md`). Tests: JUnit 5 +
> AssertJ + Mockito for targeting/decision logic and seed determinism — useful tests, not 100% coverage.

- [x] **C0. Seed plumbing (prerequisite).** Make the round seed a real, config-carried value instead of
      `PlayWorld`'s internal `new Random()`. Thread it menu → `GameConfig` → `PlayWorld`, feeding BOTH
      `MapGenerator` and the AI from the same seed. Default to a time seed when unset; allow a fixed seed
      via `game.properties` (e.g. `game.seed=`) for reproducible runs. Keep the existing seeded
      `PlayWorld(GameConfig, Random)` test constructor working. Tests: same seed → identical map + identical
      AI decisions; unset seed → varies.
      **[DONE — `GameConfig.seed` + `game.seed` (blank=time seed); `PlayWorld(GameConfig,long)` seeds
      map+AI & exposes `seed()`; copy helpers `withSeed/withPlayerNumber/withRound`; 167 tests green.]**
- [x] **C1. `...ai` package core: knobs + difficulty + RNG.** Add `AiDifficulty` enum
      (`RANDOM, EASY, NORMAL, HARD, VERY_HARD`); an immutable `AiSkills` record holding every knob; a
      factory that derives `AiSkills` from `(difficulty, seed, playerId)` — monotonic bases per level,
      seed-derived per-AI deviation, wide-band randomization for `RANDOM`. All randomness from the
      seeded stream. Tests: level ordering (e.g. accuracy VERY_HARD ≥ HARD ≥ … ), determinism for a
      fixed seed, RANDOM stays within bounds.
      **[DONE — `AiDifficulty`, `TargetMode`, `AiSkills` (validated record), `AiSkillsFactory`
      (ladder lerp + per-AI deviation + RANDOM band, RNG seeded on mix(seed,playerId)). 177 tests green.]**
- [x] **C2. `PlayerAiController` (decision + targeting).** World-aware controller in `...ai` that, per
      decision tick, scans candidate angles via `LaserPredictor`, filters out flank-block paths, scores
      reachable capture points by the active knobs (value, retake/defend bias, bounce-length preference),
      picks a target, then each frame nudges aim toward it (cursor-speed knob) and fires when aligned
      (accuracy perturbation + volley pacing). Scan is amortized/budgeted and staggered across AIs.
      Drives only `applyInput`/`fire`. Tests (Mockito for `PlayWorld`/`CaptureScoring` collaborators):
      picks a reachable point, never selects a flank-block angle, respects disc caps + volley pacing,
      defends an eroding owned point when that knob is high, behaves deterministically per seed.
      **[DONE — `AiTargeting` (reach walk + flank-first-hit) + `PlayerAiController` (utility scan/score,
      real-miss aim error, volley pacing, disc caps). 187 tests green.]**
- [x] **C3. Wire AIs into the round loop.** Build the AI controllers when the world is built
      (`PlayingState.rebuildWorldForSelectedPlayers`), assign them to the high player slots per the
      humans-low rule, and step them each frame alongside human `PlayInput` (round-robin staggered).
      Reset/recreate on round/match restart. Ensure paused/menu states don't step AIs. Tests: correct
      slot assignment for each (humans, ai) combination; AIs inactive while paused.
      **[DONE — `AiPlayers` holder (high slots, clamp, stagger) driven in `updateContinues`; built in rebuild. 202 tests green.]**
- [x] **C4. Menu options.** Add `AI_NUMBER_OPTION` (0..maxPlayers-humans) and `AI_DIFFICULTY_OPTION`
      (`RANDOM/EASY/NORMAL/HARD/VERY_HARD`) to `MenuEnum` + `GameMenu` navigation/highlight/draw, placed
      with the other setup options. Clamp AI count to the player-count selection. Tests: navigation,
      clamping, selection persists into the rebuilt world.
      **[DONE — `AI_NUMBER_OPTION` + `AI_DIFFICULTY_OPTION` wired into `GameMenu` nav/value/draw + `GameSettings`. 202 tests green.]**
- [x] **C5. `game.properties` tunables.** Add `disc.maxPerShot` (replaces the implicit single-disc-per-
      trigger / hardcoded behaviour) and an `ai.*` block: default knob intensities at NORMAL, per-level
      deviation, scan-angle count, decision interval, per-frame scan budget, and a master toggle per
      knob (skills on/off). Loaded via the existing per-key fallback in `GameConfigLoader`. Tests:
      loader reads new keys, falls back per-key when absent.
      **[DONE — `disc.maxPerShot` + `AiConfig` (`ai.scanAngles/scanBudgetPerFrame/skillsEnabled`); scanAngles drives the live build; AI disc caps clamped to config. 202 tests green.]**
