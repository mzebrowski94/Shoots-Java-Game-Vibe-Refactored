## 🎮 GAME RULES AND MECHANICS
This is a **top-down multiplayer shooter (bullet hell / physics-based)** for **1 to 4 players**.

### 🧩 CORE GAMEPLAY
* Each player controls a **fixed base** placed at a specific position on the map.
* Players **do not move** — they interact with the game only by **shooting disks**.
* Disks are fired in a chosen direction and **bounce off walls (blocks)**.

---

### 🎯 OBJECTIVE
* The goal is to **capture control points** distributed across the map.
* Control points require **multiple hits** to be captured.
* Points can be:
    * neutral
    * partially captured
    * fully controlled by a player
* Players can **steal points from opponents** by hitting them repeatedly.

---

### 💥 DISK PHYSICS
* Disks:
    * move in straight lines
    * reflect off walls (angle = angle of reflection)
    * persist in the world for some time or until conditions are met
    * have a limited bounce count
    * **speed up a little with every wall bounce**, so a long bouncing shot arrives faster than a slow lob
* The gameplay heavily relies on:
    * **predicting bounce trajectories**
    * **indirect shots using environment geometry**

---

### ⚡ POWER SHOTS
* **Tap** the shoot key for a normal disk — the standard shot.
* **Hold** the shoot key to charge up. A ring fills around your base, and the moment it's full your base
  automatically launches a single **power disk**. Let go before it fills and the charge is cancelled.
* A power disk is stronger than a normal one: it's **faster**, **bounces more times**, and **captures a
  point harder** — one hit counts for several normal hits.
* You only get one power disk per charge, so time your holds.

---

### 🛰️ BASE DISRUPTION
* Land a disk on an **opponent's base** and you **disrupt** them.
* While disrupted, that player **can't shoot** and their **aiming laser disappears** — they're stuck for a
  few seconds.
* **It costs you, too.** The disk you hit with gets **stuck rattling inside their base** for the whole
  disruption instead of bouncing away, so you're down one disk until it ends. Spamming base attacks
  leaves you with fewer disks to play with — so pick your moment.
* When the disruption wears off, the victim gets a brief **shielded moment**: they can shoot again right
  away, but you **can't disrupt them again** until the shield fades. Nobody can be locked down forever.
* You'll see it clearly: a disrupted base flickers with a **glitchy distortion**, and a base that's just
  recovered is wrapped in a **spinning shield** that slows down as the protection runs out.
* You can't disrupt your **own** base, and a disk simply passes through a base that's already disrupted
  or still shielded.

---

### 🤖 COMPUTER PLAYERS
* You can add computer-controlled players. They play by the same rules — they aim and shoot like you do.
* Tougher opponents play sharper: they aim better, react faster, and are **more aggressive about
  disrupting your base**.

---

### 🔦 AIMING ASSIST (LASER)
* Each player has a **laser aiming guide**
* The laser shows:
    * initial trajectory
    * several predicted reflections
* This helps players plan complex shots
* Your laser **switches off while your base is disrupted**, and comes back once you can shoot again.

---

### 🗺️ GAME WORLD
* The map consists of:
    * **static blocks (walls)** used for bouncing
    * **player bases** (spawn points for disks)
    * **capture points** (targets to control)

---

### 🏆 WIN CONDITION
* The game is played in **time-limited rounds**
* At the end of the round:
    * the player with the **highest number of controlled points wins round**
* At the end of all rounds:
    * the player with the **highest sum of controlled points wins game**
---

### 📊 UI CONTEXT (IMPORTANT FOR UNDERSTANDING GAME STATE)
* Top panel:
    * round timer
* Side panel:
    * current round number
    * player scores (current + previous round)
    * number of rounds won
