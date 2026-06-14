# Hello! :wave:

#### My old Java Game VIBE REFACTORED :wrench:
The first version of the program was based on pure Java and pure pleasure :wink:
One day I came up with an idea to bring the project back to life using the experience gained and power of VIBE REFACTOR.

Initial code was written many years ago with Java 8 and AWT (can be seen in initial commit).
The goal is to practice vibe coding and give the game a new life.

Game rules and mechanics are described in [Game rules](GameRules.md) file.

#### PREVIEW - gifs from first version of game: 
1. One player game: 

<img src="preview/Project_Shooots_v1_one_player.gif?raw=true" width="50%" height="50%">

2. Two players game:

<img src="preview/Project_Shooots_v1_two_players.gif?raw=true" width="50%" height="50%">

3. Four players game:

<img src="preview/Project_Shooots_v1_four_players.gif?raw=true" width="50%" height="50%">

4. Score board:

<img src="preview/Project_Shooots_v1_score_board.gif?raw=true" width="50%" height="50%">

**GUIDE:**
1. What you see on board:

<img src="preview/Project_shoots_guide_1.png?raw=true" width="75%" height="75%">

UPPER PANEL
- I. Timer (Figure 2a)
In the top panel of the game there is a timer, showing how much time is left until the end of the currently played round.
- II. Title (Figure 2b)
In the upper right corner there is the title name of the game "Project Shooots!".

SIDE PANEL
- I. Rounds counter (Figure 2c)
In the upper part of the side panel there is a rounds counter showing the number of the currently played round.
- II. Points and names of players (Figure 2d)
In the middle part of the side panel are the names of players and point strips showing the current number of points scored. Each name and bar is adapted to the player's color.
The gray bar in the spot bar shows the number of points scored in the previous round (Figure 2e)
- III. Point field (Figure 2f)
Under the point strips there are point fields describing how many rounds the player has won so far.
IV. Author (Figure 2g)
At the very bottom of the side panel there is information about the author of the program.

<img src="preview/Project_shoots_guide_2.png?raw=true" width="75%" height="75%">

CENTRAL PANEL
- I. Block (Figure 3a)
The basic element of the game world, the walls from which the discs fired by players are reflected.
- II. Disk (Figure 3g)
The basic element that the player influences the game world. The disks are fired in the direction in which the cursor is pointing, they are used to take over the points.
- III. The point (Fig. 3b) and (Fig. 3c) and (Fig. 3d) The field that players gain by hitting them with discs, can be in different
states: not taken over (Fig. 3b), gained for eg: 2 points (Fig. 3c), gained for eg: 4 points (Fig. 3d)
- IV. Player's base (Figure 3e)
Each player has its own base, i.e. the central point from which players fire their discs.
- V. Laser (Figure 3f)
The laser serves as a help for the player, indicating the path on which the disc will be launched in a given direction.
