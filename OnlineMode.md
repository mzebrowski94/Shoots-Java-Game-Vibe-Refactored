# ADR-0001: Online Multiplayer — Host-Authoritative Deterministic Lockstep

**Status:** Accepted (design) — implementation pending
**Date:** 2026-06-25
**Deciders:** Mateusz (owner/architect)
**Scope of v1:** LAN + manual-IP internet, listen server, up to 4 human players, no AI in online matches (input pipeline kept AI-ready).

> This is the authoritative design for the `online` feature. The staged task backlog lives at the
> bottom (→ becomes `NewFeatures.md` cluster F). Established runtime contracts that survive
> implementation get promoted into `refactor/STATE.md`; keep this doc as the *rationale + spec*.

---

## Context

The goal is a **simple, debuggable, maintainable** online mode: one player hosts (listen server),
others join over LAN (manual IP or auto-discovery) or the internet (public IP / VPN-LAN such as
Tailscale/Hamachi). No dedicated game servers.

The decision is shaped less by "what's the standard netcode" and more by **what this engine already
is**. Four facts dominate:

1. **The simulation is deterministic by construction.** `refactor/STATE.md`: the reflection geometry
   is "a pure function of (start, direction, grid)"; disc movement and reflection have "no RNG/time";
   AI is seeded and "reproducible for a given seed + difficulty"; per-round maps are
   `mapSeedFor(roundIndex)` — "fully reproducible per master seed." The refactor explicitly called
   this out as a foundation for "replay / online prediction."
2. **Player input is already tiny and abstracted.** `PlayWorld.applyInput(playerId, AimInput{NONE|
   LEFT|RIGHT}, shoot)` + `applyShoot(playerId, shootHeld)`. One player's entire per-tick input is
   ~3 bits. `InputBridge` already splits "EDT writes / loop reads via `poll()`", and `PlayInput`
   already maps actions → per-player intent.
3. **An AI is "just another input source."** It drives the same `applyInput`/`fire` API a human does;
   nothing special-cases it in the hot loop. A *remote human* is the same shape.
4. **Fixed-timestep loop at 120 Hz.** `GameLoop.UPDATES_PER_SECOND = 120`; `world.step()` advances
   exactly one fixed step; `PlayWorld` times charge/disruption in 120 Hz ticks.

**Constraints:** Java 25 (no preview/incubator APIs); no new heavy dependencies — `java.net`
blocking sockets only; new code goes in an **AWT-free, unit-testable `net` package** (mirroring how
`world` and `ai` were added); the **offline / hotseat path must stay byte-for-byte unchanged**.

When the simulation is deterministic and input is this small, you do not ship world state — you ship
**inputs**, and every peer re-simulates the identical world. That is **deterministic lockstep**, and
for this engine it is *both* the best fit *and* the least code, because it deletes the single largest
component of the "standard" design: the full world serializer.

---

## Decision

Build **host-authoritative deterministic lockstep**:

- **Topology:** listen server, star. Host = a normal player **plus** a server thread; clients connect
  to the host. (Keeps the listen-server model from the original notes.)
- **Steady state:** each *command frame*, every peer sends its **local human input** to the host; the
  host assembles the full input set for that frame, stamps it to an authoritative tick, and
  broadcasts it; every peer (host included) applies that set and runs the fixed `step()`s for the
  frame. Identical inputs + identical master seed ⇒ identical `PlayWorld` on every peer. **No world
  snapshots in the steady path.**
- **Round/match flow is host-driven.** The host owns phase transitions and the round clock and
  broadcasts them as tiny tick-stamped control events. Clients do not independently decide phase
  changes (this sidesteps the wall-clock / render-animation determinism gap — see *Determinism*).
- **Maps are never transferred.** Clients regenerate each round's map from the master seed +
  round index (`mapSeedFor`), identically.
- **Transport:** TCP, length-prefixed **minimal text protocol**, **thread-per-connection** blocking I/O (≤4 sockets).
- **Cadence:** keep the sim at **120 Hz**; run a **command frame at ~30 Hz** (4 sim steps per frame);
  apply **input delay of ~3 frames (~100 ms)** to hide latency.
- **Connectivity (v1):** **LAN = automatic discovery** (host beacons over UDP; joiner picks from a live
  list — no IP typing) **+ manual IP**; **internet = manual `IP:port` only** (nothing broadcasts across
  the internet). Every match gets a short host-generated **match code** (e.g. `ABCXYZ`) for easy
  identification. **No lobby, no UPnP.**
- **AI:** not spawned in online matches in v1. The input pipeline treats every slot uniformly, so AI
  can be re-enabled later **with zero protocol change** — being deterministic and seeded, each peer
  would compute the AI slots locally and identically (no AI traffic ever).

---

## Options Considered

### Option A — Deterministic lockstep (input-sync) — **chosen**

| Dimension | Assessment |
|-----------|------------|
| Complexity | **Low-Med** — no world serializer; reuses existing input + determinism |
| Bandwidth | **Tiny, constant** — ~3 bits/player/frame, independent of disc count |
| Maintenance | **Low** — new gameplay state (power, disruption, future) needs *no* netcode change |
| Fit to engine | **Excellent** — input API + determinism + AI-as-input already done |

**Pros:** smallest code; microscopic bandwidth (ideal for a bullet-hell with many discs); replay for
free (seed + input log); AI free over the wire later; reuses the determinism the refactor already
bought; offline path untouched.
**Cons:** requires *strict* determinism (any divergence desyncs); input delay is felt by everyone,
including locally; one slow/lost peer stalls all peers (lockstep); mid-round join is hard.

### Option B — Snapshot + interpolation (the original notes' model)

| Dimension | Assessment |
|-----------|------------|
| Complexity | **High** — must serialize *all* world state and maintain it as features grow |
| Bandwidth | **Scales with disc count** — every disc, every snapshot |
| Maintenance | **High** — power/disruption already added entity fields; each new feature touches the serializer |
| Fit to engine | **Poor** — discards the determinism that was deliberately built |

**Pros:** robust to packet loss and minor non-determinism; mid-match join is easier; clients need not
be deterministic.
**Cons:** large, ever-growing serializer; bandwidth grows with the very thing this game spawns a lot
of (discs); interpolation delay + no prediction feels laggy; throws away existing work.

### Option C — Hybrid (lockstep + periodic snapshot resync)

| Dimension | Assessment |
|-----------|------------|
| Complexity | **High** — you build *both* paths |
| Bandwidth | Low steady-state, spikes on resync |
| Maintenance | **Highest** — two code paths to keep correct |
| Fit to engine | Good steady-state, but premature |

**Pros:** most robust; snapshot recovers from any desync.
**Cons:** most up-front work. **We adopt the *idea* in a minimal form only:** a per-second **state-hash
exchange** for desync *detection*, and a full snapshot reserved as a **debug / last-resort resync
tool** — not part of the steady path. The serializer therefore stays small and optional.

---

## Trade-off Analysis

The crux is **determinism cost vs serialization cost**. Option B pays serialization cost forever
(every feature touches it, bandwidth grows with discs). Option A pays determinism cost *once* — and
~90% of it is already paid by the refactor. The residual determinism work (below) is bounded and
mostly lives in one place (the round/match flow), not scattered through the hot loop.

Lockstep's genuine downsides are latency and stall-coupling. For this game they are acceptable: it is
**round-based** (joins happen in a lobby, not mid-round, so "hard mid-round join" barely applies);
it is **capture-the-point, not twitch-FPS** (≈100 ms uniform input delay is imperceptible for
aim-and-shoot); and at **≤4 players in a star** the stall surface is small and bounded by the input
buffer + max-frame clamp the loop already has.

TCP (vs UDP) is the right call *because* we chose lockstep: every input is mandatory (you can't skip
one), so TCP's in-order reliability is a feature, not the head-of-line liability it would be for a
drop-old-snapshots design. A minimal human-readable text codec (vs JSON/binary) is right while messages
are this small — JSON would add a core dependency for no measurable gain, and binary costs the
debuggability that is an explicit goal. (Jackson is vendored in `.m2` if a richer payload ever justifies it.)

---

## Architecture

### New `net` package (AWT-free, unit-testable)

```
pl.mzebrows.shoots.net
├── NetTransport            (interface)  send/receive framed messages
│   ├── LoopbackTransport   single-process, in-memory queues (tests + "run both locally")
│   ├── TcpServerTransport  host side: accept loop + per-connection reader threads
│   └── TcpClientTransport  client side: one socket, one reader thread
├── NetMessage              (sealed interface + record variants — see Protocol)
├── MessageCodec            text <-> NetMessage, length-prefixed framing
├── GameServer              host: aggregates inputs, assigns ticks, broadcasts input frames + control
├── GameClient              client: sends local input, receives input frames + control
├── LockstepCoordinator     the gate: buffers inputs per frame, applies input delay, releases a
│                           TickInputSet only when complete; drives world.step() N times/frame
├── TickInput               record(AimInput aim, boolean shootHeld)      // one player, one frame
├── InputFrame              record(long frame, TickInput[] bySlot)       // all players, one frame
└── NetSession              façade wiring transport+server/client+coordinator for app/state
```

Mirrors the `world`/`ai` precedent: interface-first, manual constructor DI, no Spring, no AWT, fully
testable via `LoopbackTransport`. No new Maven dependencies.

### Integration with the existing loop (small, `PlayWorld` untouched)

Today, `PlayingState.updateContinues` is:

```java
if (settings.isPlayerKeyboardAvailable()) {
    PlayInput.apply(input, world);   // reads ONE local InputBridge for ALL players (hotseat)
    aiPlayers.think(world);
}
world.step();                        // one fixed step, every frame
```

`PlayInput.apply` currently fills **all four** players from the single local keyboard. Online changes
exactly that seam:

- **Local human slot(s):** read from the local `InputBridge` as today, producing a `TickInput`.
- **Remote human slots:** read from the `LockstepCoordinator`'s buffer for the current command frame.
- **The step is gated:** advance the world's frame only when the coordinator has the complete
  `InputFrame` for `frame - inputDelay`; then run the 4 sim `step()`s for that frame.

Conceptually, online `updateContinues` becomes:

```java
session.submitLocalInput(localTickInput(input));     // send my input to host
InputFrame f = coordinator.tryReleaseFrame();        // null until all slots present (lockstep gate)
if (f != null) {
    for (int s = 0; s < world.playerCount(); s++) {
        world.applyInput(s, f.bySlot()[s].aim(), false);
        world.applyShoot(s, f.bySlot()[s].shootHeld());
    }
    for (int i = 0; i < STEPS_PER_FRAME; i++) world.step();   // 120/30 = 4
}
```

**Offline / hotseat is unchanged:** it keeps today's `PlayInput.apply + world.step` path (or routes
through a `LoopbackTransport` no-op coordinator that releases every frame immediately). A
`GameMode { OFFLINE, HOST, CLIENT }` selects the path; single-player and hotseat keep their exact
current behavior.

### Topology, slots, join

- **Join happens in a lobby state, pre-match.** On connect the host assigns the client a **player
  slot (0–3)** and sends `WELCOME` with the **master seed, match code, slot, player count, and the
  gameplay config subset** needed to build an identical `PlayWorld`. No mid-round join in v1.
- **Match code.** On session creation the host generates a short random **match code** (6 uppercase
  letters, e.g. `ABCXYZ`) so a match is easy to identify among players. It is match METADATA, never
  part of the simulation (excluded from the lockstep state/hash): carried in `WELCOME`, beaconed in LAN
  discovery, and shown in the menu. LAN: it labels each entry in the discovered list (the human-friendly
  pick-key); internet: it is a display/confirmation label only (you still connect by `IP:port`).
- **Connect modes (v1).** **LAN = automatic discovery** — the host beacons over UDP and the joiner
  picks a match from a live list (no IP typing). **Internet = manual `IP:port` only** — no broadcast
  crosses the internet, so the joiner types the host's address (public IP or VPN-LAN); the match code is
  shown for confirmation.
- **Slot mapping** follows the existing fixed scheme (P0 bottom, P1 top, P2 left, P3 right) so spawn
  side / aim are stable and identical on every peer (already a `PlayWorld` contract).
- **Disconnect (v1):** host loss ⇒ match ends (no host migration); client loss ⇒ host pauses, then
  either drops the slot or aborts the match (policy chosen during F4). Keep it blunt and obvious.

---

## Determinism plan (the real engineering work)

The physics is ready. The gap is that the **round/match flow is entangled with wall-clock and render
timing** — that, not the hot loop, is where lockstep would desync:

1. **Round timer uses wall-clock.** `PlayingState.tickRoundSecond()` advances the round on
   `System.nanoTime()` deltas. → **Count sim ticks, not nanoseconds**, so the round ends on the same
   tick on every peer. (Host remains the authority and broadcasts the round-end event.)
2. **Phase transitions are gated by render animations.** `BEGIN→CONTINUES` and `ENDS→next` wait on
   `animationsEnded()` (render-side `screen/counter` timers, which differ per machine). → **Host
   decides phase transitions** and broadcasts tick-stamped `CONTROL` events; clients follow. Local
   animations may still play for feel, but they no longer *drive* simulation phase.
3. **Map data never crosses the wire.** `mapSeedFor(roundIndex)` is deterministic; clients rebuild
   identical maps from the master seed. Confirm `resetRound()` ordering is identical on all peers
   (it is host-triggered via the round-advance control event).
4. **Sim purity audit (cheap, do once):**
   - **`HashMap`/`HashSet` iteration inside the sim.** Saw `discOwners` (`IdentityHashMap`, lookup
     only) and `blockHitByTile` (render-only flash effects). Both *look* non-authoritative; confirm
     no sim-affecting code iterates them in hash order. The disc list is an index-loop `ArrayList`
     (already a contract) — good.
   - **Transcendental math.** `Math.sin/cos/atan2` (aim/laser/tracer) *may* differ by ≤1 ulp across
     CPU/OS. Java is strict-FP by default (since 17) for `+ - * / sqrt`, so only transcendentals are
     at risk. **v1 (same build on LAN): `Math` is effectively identical — ship it.** If the
     state-hash ever diverges cross-machine, switch the sim's transcendental calls to `StrictMath`
     (bit-identical everywhere). Decide via the hash test, don't pre-optimize.
   - **AI purity (for the *later* re-enable):** confirm `PlayerAiController.think` reads only world
     state + seeded RNG (no local input, no wall-clock). If pure, AI works online for free.
5. **Desync detection.** Each peer hashes a small authoritative slice of `PlayWorld` (disc count +
   quantized positions + capture %s + scores) every ~1 s and sends the hash to the host; the host
   compares against its own. Mismatch ⇒ log loudly (tick + first differing field). This is the only
   place a (debug) serializer is ever needed.

---

## Protocol (message catalog, text, length-prefixed)

> Wire format: one minimal human-readable line per message (`TYPE|key=value|...`), length-prefixed for
> TCP framing (`MessageCodec`). Text was chosen over JSON/binary for zero-dependency debuggability given
> the tiny flat messages; Jackson is vendored in `.m2` if a richer payload (e.g. a full nested
> `GameConfig` in `WELCOME`) ever justifies it.

**Client → Server**
- `JOIN { name, protocolVersion }`
- `INPUT { frame, aim, shootHeld }` — one local player's input for a command frame
- `HASH { tick, hash }` — desync check (optional but recommended)
- `PING { t }` (optional)

**Server → Client**
- `WELCOME { slot, playerCount, masterSeed, matchCode, config }` — everything needed to build an identical world
- `INPUT_FRAME { frame, inputs[] }` — authoritative full input set for a command frame
- `CONTROL { tick, event }` — `START_ROUND` / `END_ROUND` / `MATCH_OVER` / `PAUSE` / `RESUME`
- `PONG { t }` (optional)

`config` in `WELCOME` carries only the gameplay-affecting knobs (grid, disc, power, disruption, round,
seed) — i.e. enough to reconstruct `GameConfig`; rendering/`graphic.properties` stays local.
`matchCode` is the short host-generated identifier (display/identification only, not simulation state).

**Discovery (UDP broadcast/multicast, LAN only)**
- `ANNOUNCE { matchCode, name, host, port, players, joinable }` — the host beacons this periodically on
  the LAN; the joiner collects beacons into a live match list (entries expire when the beacons stop).
  There is no internet equivalent — internet join is manual `IP:port`.

---

## Consequences

**Easier / better:** microscopic, constant bandwidth; replay for free (record seed + input log);
no world serializer to build or maintain; AI re-enables later with zero protocol change; offline /
hotseat path untouched; the design directly reuses the determinism the refactor already paid for.

**Harder / accepted:** the simulation must *stay* strictly deterministic — new gameplay must keep the
"no RNG/time in the hot path" rule (already a project convention); a slow/lost peer stalls all peers
(mitigated by input delay + the loop's existing max-frame clamp; bounded at ≤4 in a star); the
round/match flow must be refactored to be tick-driven / host-authoritative; no mid-round join.

**To revisit later:** binary protocol (only if messages grow); lobby server + UPnP for friction-free
internet; host migration; client-side prediction (only if the ~100 ms input delay ever feels bad —
unlikely for this genre); AI in online matches.

---

## Action Items — staged backlog (→ `NewFeatures.md` cluster F)

Each cluster ends with `./mvnw test` green; new logic is tested via `LoopbackTransport` without sockets.

- **F0. Determinism prerequisite.** **[DONE — 259 tests green.]** Round time counted in sim ticks, not
  `nanoTime` (`PlayingState.STEPS_PER_ROUND_SECOND`); lockstep-invariant guard added
  (`PlayWorldDeterminismTest`: two same-seed worlds + identical scripted inputs stay bit-identical every
  tick for 600 ticks, plus a progress guard). Sim purity audited (`discOwners` lookup-only,
  `blockHitByTile` render-only; disc list is an index-loop). **Re-scoped:** phase transitions are gated
  by `animationElementEnd`, which is set during DRAW (render-coupled, absent headless), so making them
  deterministic is folded into F2 (host commands transitions; clients obey) rather than churn the
  intro/outro feel now. `Math` vs `StrictMath` deferred to the F5 hash test.
- **F1. `net` core + `LoopbackTransport`.** `NetTransport`, `NetMessage` + `MessageCodec`,
  `TickInput`/`InputFrame`, `LockstepCoordinator` (input delay + completeness gate). Route the
  existing loop's input through the coordinator **in-process** (no sockets) and prove the world steps
  identically to the direct path. *Tests: coordinator gates until complete; loopback == direct.*
- **F2. Host round-flow authority.** **[DONE — 271 tests green.]** `net.RoundFlow` owns the
  BEGIN/CONTINUES/ENDS phase (OFFLINE local / HOST local+broadcast / CLIENT follow) over a
  `ControlChannel` (`LoopbackControlChannel` now); `PlayingState` delegates phase ownership to it
  (OFFLINE default = unchanged). Subsumes the phase-transition determinism deferred from F0. Tests:
  a CLIENT reproduces the HOST's phase sequence across a multi-round match incl. match-over. The named
  `GameServer` aggregator + socket transport land in F3.
- **F3. TCP transport, 2 processes on localhost.** **[DONE — 281 tests green.]** `MatchCode`,
  `NetMessage` (sealed) + text `MessageCodec` with length-prefixed framing; `TcpConnection` (daemon
  reader + framed send), `TcpClientTransport` (connect + `JOIN` + await `WELCOME`), `TcpServer` (accept,
  slot assign, `WELCOME` = slot + player count + master seed + match code; broadcast; slot-tagged poll).
  Client config is reconstructed locally from seed + player count (peers share a build); full-config
  payload deferred. *Tests: codec round-trips (incl. match code) + framing across 1-byte reads
  (`MessageCodecTest`), `MatchCodeTest`, localhost handshake + bidirectional exchange (`TcpTransportTest`).*
- **F1–F3 online-loop integration.** **[DONE — 283 tests green.]** `OnlineHost` (aggregate via
  `LockstepCoordinator` → broadcast `Frame` → apply) + `OnlineClient` (send input → apply host frames →
  follow `Control`) over the F3 TCP transport, step-driven for the game loop. `OnlineLoopIntegrationTest`:
  host & client worlds stay bit-identical every frame over real localhost sockets (random host seed,
  delivered via `WELCOME`); a host `CONTROL` drives the client's `RoundFlow`. Remaining: wire
  `OnlineHost`/`OnlineClient` into the AWT `GameLoop`/`PlayingState`, selected from the F6 menu.
- **F4. Connect UX — LAN auto-discovery + internet manual IP.** **[DONE — 291 tests green.]**
  `LanBeacon` (UDP, `broadcast()` on port 48888) + `LanDiscovery` (listener + TTL'd live table; source IP
  taken from the packet) + `LanAnnouncement`; the joiner picks a `DiscoveredMatch`, and the internet path
  uses a manual `HostAddress` (`ip:port`). Disconnect policy: host-loss ⇒ match ends; client-loss ⇒ host
  pauses then drops the slot / aborts (detection via `isOpen()` / `connectedClients()`; enforced in F6).
  *Tests: `LanAnnouncementTest`, `LanDiscoveryTest` (add/expire + loopback-UDP smoke), `HostAddressTest`.*
- **F5. Up to 4 players + desync hash.** **[DONE — 295 tests green.]** 4 players proven over TCP
  (`FourPlayerOnlineTest`: host + 3 clients bit-identical via `WorldHash`). Desync net: `WorldHash`
  (quantized authoritative state) + `NetMessage.Hash`; `OnlineHost` compares each client's hash to its
  own per-frame hash and flags mismatches (`desyncCount`/`lastDesyncFrame`). *Tests: `WorldHashTest`,
  `OnlineDesyncTest` (bogus hash detected over TCP), `FourPlayerOnlineTest`.*
- **F6. Online launch + loop wiring.** **[PLAYABLE via config — 296 tests green.]** `OnlineConfig`
  (`online.mode`/`online.host`/`online.port`) + `OnlineSession` (bootstraps host/client, builds the
  shared world from the master seed, drives one lockstep command frame per tick). `PlayingState` online
  branch (OFFLINE byte-identical) follows the session's `RoundFlow`; `GameLoop` builds the session at
  startup when `online.mode != off`. Proven over loopback by `OnlineSessionTest`. **Deferred:** the
  in-menu **"Play online"** button + sub-screen (AWT, unverifiable here — config launch covers playtest);
  input-delay / command-frame tuning for internet latency (v1 = D=0 lockstep, ideal on localhost/LAN);
  in-loop disconnect-policy enforcement. See "Playtesting online (v1)" below.
- *Deferred:* lobby server, UPnP, binary protocol, host migration, AI-in-online, client prediction.

---

## Playtesting online (v1)

Online launch is **config-driven** for now (the in-menu button is deferred). Edit `game.properties`,
launch one instance per player, and a match starts automatically.

- **Host:** set `online.mode=host`, launch. The log prints `Hosting match <CODE> on port 48900` and the
  host beacons on the LAN. Player count comes from `menu.initialPlayers` (default 2; raise it for 3–4p).
- **Join over LAN:** set `online.mode=client`, leave `online.host` blank, launch — it auto-discovers the
  host's beacon and joins the first match found (no IP needed). VPN-LANs (Tailscale/Hamachi) count as LAN.
- **Join by address (internet / same machine):** set `online.mode=client`, `online.host=<hostIP>`,
  `online.port=48900`. For two instances on ONE machine, use `online.host=127.0.0.1`.
- **Controls:** your local player always uses the **P1 (arrow) keys**, mapped onto whichever slot the
  host assigned you (host = slot 0 / bottom; first client = slot 1 / top; …).

**What "working" looks like:** both windows show the same discs moving identically every frame, and the
host log shows no `DESYNC` lines (the per-frame `WorldHash` check). 

**Known v1 limitations to expect during playtest** (these are the deferred items, not bugs):
- No in-menu Play-Online screen yet — launch is via `game.properties`.
- Cadence is a zero-delay 120 Hz lockstep: silky on localhost/LAN, but it stalls to network pace on a
  high-latency internet link (each frame waits for the round-trip). Internet smoothness needs the
  input-delay / command-frame-rate tuning, which is the next step and wants real-latency playtest data.
- Disconnect handling is minimal (the detection hooks exist; the loop doesn't yet pause/abort on a drop).
- Round/phase sync across peers and per-round map regeneration are wired but unverified live — watch for
  the two sides drifting at round boundaries and report it.

## Open items to confirm during implementation

- Exact **input delay** (start at 3 command frames ≈ 100 ms; tune to feel + jitter).
- **`Math` vs `StrictMath`** — decided empirically by the cross-machine hash test (F5), not up front.
- **Disconnect policy** — DECIDED (F4): host-loss ends the match; client-loss pauses then drops the slot
  / aborts. Detection lives in the transport (`isOpen()` / `connectedClients()`); enforcement is wired
  into the loop in F6.
