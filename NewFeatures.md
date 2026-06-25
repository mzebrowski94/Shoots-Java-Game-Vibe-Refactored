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
- [ ] Implement a `SoundManager` with a small `javax.sound.sampled` clip pool so overlapping SFX
      (shots, hits/captures, explosions) don't cut each other off or stutter. Load clips once and
      reuse; fail with log when a sound asset is missing. Wire SFX triggers into the
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

## [x] D. Gameplay feel: acceleration, corner glances, power shots
> Four tightly-related gameplay additions on top of the refactored model. All physics stays
> deterministic (no RNG/time in disc movement or reflection) so motion remains reproducible for
> replay / online-prediction. Verified with `./mvnw test` (234 green).

- [x] **D1. Disc acceleration per wall bounce.** Each wall bounce multiplies a disc's realised speed
      by `disc.bounceSpeedGain`, capped at `moveSpeed * disc.maxSpeedFactor`. Carried on the pooled
      `Entity` (`speedGainPerBounce`/`maxMoveSpeed`, set at spawn) so `DiscSystem` stays generic.
      `DiscSystem` now integrates each frame in safe-sized sub-steps (`safeStep = min(moveSpeed,
      ballCollisionSize)`) so fast/accelerated discs never tunnel through walls; a base-speed disc with
      no gain sub-steps to exactly one step (legacy behaviour). Tests: `DiscAccelerationTest`.
- [x] **D2. Corner glance instead of redundant reversal.** `UniformGridCollider` convex-corner case
      (only the diagonal tile solid) now flips a SINGLE velocity-dominant axis (a ~90° glance) instead
      of reversing both, eliminating the redundant 180° back-track. Concave/inner corners still reverse.
      The flip axis is a pure function of the travel angle (`|sin| vs |cos|`, tie -> X), so it is fully
      deterministic. The predictive laser uses the same `collider.resolve`, so it matches automatically.
      Tests updated in `UniformGridColliderTest`.
- [x] **D3. Charged power shot.** Hold the shoot key to fill a charge ring on the base; it auto-fires a
      power disc when full (a tap still fires a normal disc immediately on press). Power disc = faster
      (`power.speedFactor`), more bounces (`power.maxBounces`), and `power.captureStrength` capture
      levels per hit (`CapturePoint.tryCapture(player,strength)`). Charge state + `applyShoot`/`firePower`/
      `chargeProgress` live in the AWT-free `PlayWorld`; `PlayInput` drives it from the held key.
      Renderer: a filling/brightening charge ring on the base + a glow on power discs. Config:
      `PowerShotConfig` (`power.*`). Tests: `PowerShotChargeTest`, `CapturePointStrengthTest`.
- [x] **D4. AI power shots by difficulty.** New `AiSkills.powerShotTendency` knob (rises up the
      EASY..VERY_HARD ladder). `PlayerAiController` fires a power shot on "long-range" targets (bounce
      path >= `ai.powerShotMinBounces`) gated by the tendency; AIs bypass the human charge UX and call
      `PlayWorld.firePower` directly. Master toggle `ai.powerShotEnabled`. Tests: `AiPowerShotTest`,
      `AiSkillsFactoryTest`.


## [x] E. Base disruption + AI aggressiveness
> Two coupled additions on top of cluster D: a new gameplay mechanic where hitting an opponent's base
> silences them, and a new AI behavioural knob driving how often AIs go for it. Verified with
> `./mvnw test` (257 green). Renderer overlays verified headlessly.

- [x] **E1. Base disruption mechanic.** A disc that enters an opponent's base parks on it
      (`Entity.parked`, owned by `PlayWorld`, not advanced/retired by `DiscSystem`) and disrupts the
      victim for `disruption.durationSeconds`: shooting blocked (`fire`/`firePower`/`applyShoot`) and laser
      off (`predictLaser`->0). When it ends the parked disc is freed (the attacker's slot cost) and the
      victim gets a `disruption.graceSeconds` immunity window (may shoot, can't be re-disrupted). Detection
      via `GridPathTracer.PathVisitor.onPlayerBase` + `DiscEventSink.onPlayerBaseHit`. Config
      `DisruptionConfig` (`disruption.*`). Overlay-only `DisruptionRenderer` (animated glitch on disrupted
      bases, animated shield in grace; base graphics untouched). Tests: `BaseDisruptionTest`.
- [x] **E2. AI aggressiveness.** `AiSkills.baseAttackTendency` (ladder-scaled). `PlayerAiController` folds
      opponent-base targets into its existing scan (`AiTargeting.reachIncludingBases`, own-origin base
      ignored), gated by the knob + `ai.baseAttackEnabled`. Per-skill on/off `AiSkillToggles`
      (`ai.skill.*`, checked once at round start) added for EVERY AI skill so any one behaviour can be
      switched off without disabling the AI. Tests: `AiAggressivenessTest`, `GameConfigLoaderTest`.

## [ ] F. Online multiplayer (LAN + manual IP)
> Host-authoritative **deterministic lockstep**: peers sync only player INPUT (aim + shoot, ~3 bits);
> every peer re-simulates the identical world from the same master seed — no world-state snapshots.
> Chosen because the sim is already deterministic, input is already abstracted (`PlayWorld.applyInput`/
> `applyShoot`), and an AI is already "just another input source". New AWT-free, unit-testable `net`
> package (interface-first transport, `LoopbackTransport` for tests + single-process play); the
> offline/hotseat path stays unchanged behind a `GameMode { OFFLINE, HOST, CLIENT }` switch.
> **Full rationale, trade-offs, protocol, package layout, and determinism plan: root `OnlineMode.md`
> (authoritative for this topic — read it before starting).** v1 scope: LAN + manual IP, listen
> server, up to 4 human players, no AI online (pipeline kept AI-ready). TCP + a minimal text protocol, ~30 Hz command
> frame over the 120 Hz sim, ~100 ms input delay. Build/verify with `./mvnw test`; new logic tested
> via `LoopbackTransport` without sockets.
>
> **The real prerequisite is determinism of the ROUND FLOW, not the physics.** `PlayingState` advances
> the round clock on `System.nanoTime()` and gates phase transitions on render `animationsEnded()` —
> both differ per machine and would desync. F0 fixes that first.

- [x] **F0. Determinism prerequisite (round/match flow).** **[DONE — 259 tests green.]** Round time is
      now counted in SIM TICKS, not `System.nanoTime()` (`PlayingState.STEPS_PER_ROUND_SECOND` = 120), so
      the round clock advances identically on every peer. Lockstep-invariant guard added
      (`PlayWorldDeterminismTest`): two same-seed worlds fed an identical scripted input stream stay
      bit-identical EVERY tick for 600 ticks, plus a "sim actually progresses" guard. Sim-purity audit:
      `discOwners` (IdentityHashMap) is lookup-only and `blockHitByTile` is render-only — neither affects
      sim order; the disc list is an index-loop. **Re-scoped from the original plan:** the
      `BEGIN→CONTINUES→ENDS` phase transitions are gated by `animationElementEnd`, which is set during
      DRAW (machine-dependent render cadence, absent when headless). Making those deterministic is folded
      into **F2**, where the host commands transitions and clients obey — so clients need no local phase
      timing. `Math` vs `StrictMath` decision deferred to the F5 hash test.
- [ ] **F1. `net` core + `LoopbackTransport`.** `NetTransport` (interface), `NetMessage` + JSON
      `MessageCodec` (length-prefixed framing), `TickInput`/`InputFrame`, `LockstepCoordinator`
      (input-delay buffer + completeness gate). Route the existing loop's input through the coordinator
      IN-PROCESS (no sockets) and prove the world steps identically to the direct path. Tests:
      coordinator gates until a frame is complete; loopback path == direct path.
- [x] **F2. Host round-flow authority.** **[DONE — 271 tests green.]** New `net.RoundFlow` owns the
      BEGIN/CONTINUES/ENDS phase, mode-aware: OFFLINE decides locally (unchanged), HOST decides locally
      AND broadcasts each transition as a tick-stamped `ControlEvent` over a `ControlChannel`
      (`LoopbackControlChannel` for now), CLIENT follows those events instead of its render-coupled
      timers. `PlayingState` now delegates phase ownership to `RoundFlow` via a new 4-arg constructor
      (OFFLINE by default, so the single-machine path is byte-for-byte unchanged). This SUBSUMES the
      phase-transition determinism deferred from F0. Tests (`RoundFlowTest`/`LoopbackControlChannelTest`):
      a CLIENT reproduces the HOST's exact phase sequence across a multi-round match incl. match-over;
      client stalls without events; restart resets to BEGIN. The named `GameServer` aggregator (inputs +
      clients + control over a socket) lands with the transport in F3.
- [x] **F3. TCP transport, two processes on localhost.** **[DONE — 281 tests green.]** `MatchCode`
      (6-letter `ABCXYZ`); `NetMessage` (sealed: Join/Welcome/Input/Frame/Control) + text `MessageCodec`
      with length-prefixed framing; `TcpConnection` (daemon reader thread + framed send), `TcpClientTransport`
      (connect + `JOIN` + await `WELCOME`), `TcpServer` (accept, assign slot, `WELCOME` = slot + master
      seed + player count + **match code**; broadcast; slot-tagged poll). Match code is match metadata,
      NOT simulation state. Client rebuilds config locally from seed + player count (shared build);
      full-config payload deferred. Tests: `MessageCodecTest` (round-trips incl. match code; framing
      across 1-byte reads), `MatchCodeTest`, `TcpTransportTest` (localhost handshake + bidirectional
      exchange). NOT YET wired into the live `PlayingState` loop — that online-loop integration (combining
      the F1 coordinator + F2 flow + this transport) is the next step.
- [x] **F1–F3 online-loop integration.** **[DONE — 283 tests green.]** `OnlineHost` (aggregate all
      slots via `LockstepCoordinator` → broadcast `Frame` → apply with `LockstepApplier`) + `OnlineClient`
      (send local `Input` → apply the host's authoritative `Frame`s → follow host `Control` via a CLIENT
      `RoundFlow`), running over the F3 TCP transport; both are step-driven (no internal thread) so the
      game loop can call them per command frame. `OnlineLoopIntegrationTest` proves over REAL localhost
      sockets that host & client worlds stay BIT-IDENTICAL every frame with a RANDOM host seed (so the
      seed genuinely travels via `WELCOME`), and that a host round-flow `CONTROL` drives the client's
      phase. Remaining to make it playable: hook `OnlineHost`/`OnlineClient` into the AWT
      `GameLoop`/`PlayingState` online path, selected from the F6 menu.
- [x] **F4. Connect UX — LAN auto-discovery + internet manual IP.** **[DONE — 291 tests green.]**
      `LanAnnouncement` (UDP beacon payload; ignores non-beacon traffic), `LanBeacon` (periodic sender;
      `broadcast()` to the segment on port 48888 in prod, explicit target for tests), `LanDiscovery`
      (UDP listener + TTL'd live table that expires hosts whose beacons stop; pairs each beacon with the
      packet's source IP). The joiner picks a `DiscoveredMatch` and connects via `TcpClientTransport`;
      the internet path parses a manual `HostAddress` (`ip:port`). **Disconnect policy:** host-loss ⇒
      match ends; client-loss ⇒ host pauses then drops the slot / aborts — detection via
      `TcpClientTransport.isOpen()` / `TcpServer.connectedClients()`; enforcement is wired in F6. Tests:
      `LanAnnouncementTest`, `LanDiscoveryTest` (table add/expire with an injected clock + a real
      loopback-UDP beacon→discovery smoke), `HostAddressTest`.
- [x] **F5. Up to 4 players + desync hash.** **[DONE — 295 tests green.]** 4 players verified end to end:
      `FourPlayerOnlineTest` runs a host + 3 clients over real localhost TCP staying bit-identical
      (compared via `WorldHash`) every frame (slot assignment + 4-slot aggregation in `LockstepCoordinator`).
      Desync net: `WorldHash` (order-stable hash of quantized disc pos/angle/bounces + aim + capture points
      + scores) + a `NetMessage.Hash` the client sends; `OnlineHost` records its own per-frame hash,
      compares incoming client hashes, and flags mismatches (`desyncCount`/`lastDesyncFrame` + `log.error`).
      Tests: `WorldHashTest` (identical worlds hash equal / divergence detected), `OnlineDesyncTest`
      (matching hashes pass, a bogus hash is detected over TCP), `FourPlayerOnlineTest`.
- [~] **F6. Online launch + loop wiring.** **[PLAYABLE via config — 296 tests green; in-menu button
      deferred, see below.]** `OnlineConfig` reads `online.mode` (`off`|`host`|`client`) +
      `online.host`/`online.port` from `game.properties`. `OnlineSession` bootstraps a host (server + LAN
      beacon + `OnlineHost`) or a client (explicit `IP:port` or LAN auto-discovery + `OnlineClient`),
      builds the shared world from the master seed, and drives one lockstep command frame per tick.
      `PlayingState` gained an online branch (the OFFLINE/hotseat path is byte-identical) that drives the
      session and follows the host's `RoundFlow`; `GameLoop` builds the session at startup when
      `online.mode != off` and starts the match (offline still shows the menu). Engine proven headlessly
      over loopback (`OnlineSessionTest`: host + client stay bit-identical). **Deferred (needs a playtest
      pass / can't be verified from here):** the in-menu `PLAY_ONLINE` button + network sub-screen
      (AWT — config launch covers playtesting without it; the menu reflow risks layout overflow on the
      900px window); input-delay / command-frame-rate tuning for higher-latency internet (v1 is a D=0
      one-step-per-frame lockstep, ideal on localhost/LAN); in-loop enforcement of the disconnect policy.
      Playtest instructions: `OnlineMode.md` → "Playtesting online (v1)".

> **Deferred (post-v1, noted in `OnlineMode.md`):** lobby server, UPnP port-forwarding, binary protocol,
> host migration, AI in online matches, client-side prediction.