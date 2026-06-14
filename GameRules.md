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
* The gameplay heavily relies on:
    * **predicting bounce trajectories**
    * **indirect shots using environment geometry**

---

### 🔦 AIMING ASSIST (LASER)
* Each player has a **laser aiming guide**
* The laser shows:
    * initial trajectory
    * several predicted reflections
* This helps players plan complex shots

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
