
package pl.mzebrows.shoots.game.logic;

/**
 * Obiekt odpowiadający za obliczanie kolizji w grze
 * @author Mateusz Żebrowski, Nr albumu: 95281
 */
public class ColisionCalculator {

    int restX;
    int restY;
    boolean colision;
    int inX;
    int inY;
    int size;
    int colisionType = 0;
    int ballColisionSize = 4;

    int[][] matrix;

    int directionX = 1;
    int directionY = 1;

    boolean rightSideColision;
    boolean leftSideColision;
    boolean upSideColision;
    boolean SideColision;

    /**
     * Konstruktor obiektu ColisionCalculator
     *
     * @param gameSettings argument pobierający ustawienia gry
     */
    public ColisionCalculator(GameSettings gameSettings) {
        // c11: GameSettings.getMapMatrix() removed when legacy map state was decommissioned.
        // Legacy class kept only as a behavioural reference (see STATE.md); not constructed by the
        // live game. Disabled so the IDE raw compile (which ignores Maven <excludes>) passes.
        // this.matrix = gameSettings.getMapMatrix().mapMatrix;
        size = gameSettings.getUNIT();
    }

    /**
     * Metoda odpowiadająca za obliczenie kolizji obiektu z elementami
     * znajdujacymi się na mapie
     *
     * @param colisionPoint obiekt dla którego obliczona ma zostać kolizja
     * @return obiekt dla którego została obliczona kolizja
     */
    public ColisionPoint checkColision(ColisionPoint colisionPoint) {
        restX = (int) colisionPoint.getPosX() % size;
        restY = (int) colisionPoint.getPosY() % size;
        colision = false;
        colisionType = colisionPoint.getColisionType();
        inX = (int) colisionPoint.getPosX() / size;
        inY = (int) colisionPoint.getPosY() / size;

        directionX = colisionPoint.getDirectionX();
        directionY = colisionPoint.getDirectionY();

        rightSideColision = leftSideColision = upSideColision = SideColision = false;

        if (inX == 0 || inY == 0 || inX == 24 || inY == 24) {
            colisionPoint.setColision(true);
            return colisionPoint;
        }

        if (colisionType != 0) {
            if (matrix[inX][inY] == 2) {
                //System.out.println("Collison");
                colisionPoint.setIndexX(inX);
                colisionPoint.setIndexY(inY);
                colisionType = 0;
                colision = true;
//                colisionPoint.setColision(true);
                //colisionPoint
            }
        }
        if (!colision && colisionType != 1 && restX >= 0 && restX <= ballColisionSize) {
            rightSideColision = true;
            if (matrix[inX - 1][inY] == 1) {
                colisionPoint.setDirectionX(-directionX);
//                colisionPoint.setColision(true);
                colision = true;
                colisionType = 1;
                colisionPoint.setIndexX(inX - 1);
                colisionPoint.setIndexY(inY);

                //System.out.println("PRAWA");
            }

        }
        if (!colision && colisionType != 2 && restX >= size - ballColisionSize && restX <= size) {
            leftSideColision = true;
            if (matrix[inX + 1][inY] == 1) {
                colisionPoint.setDirectionX(-directionX);
//                colisionPoint.setColision(true);
                colision = true;
                colisionType = 2;
                colisionPoint.setIndexX(inX + 1);
                colisionPoint.setIndexY(inY);
                //System.out.println("LEWA");
            }
        }
        if (!colision && colisionType != 3 && restY >= 0 && restY <= ballColisionSize) {
            SideColision = true;
            if (matrix[inX][inY - 1] == 1) {
                colisionPoint.setDirectionY(-directionY);
//                colisionPoint.setColision(true);
                colision = true;
                colisionType = 3;
                colisionPoint.setIndexX(inX);
                colisionPoint.setIndexY(inY - 1);
                // System.out.println("DOL");
            }
        }
        if (!colision && colisionType != 4 && restY >= size - ballColisionSize && restY <= size) {
            upSideColision = true;
            if (matrix[inX][inY + 1] == 1) {
                colisionPoint.setDirectionY(-directionY);
//                colisionPoint.setColision(true);
                colision = true;
                colisionType = 4;
                colisionPoint.setIndexX(inX);
                colisionPoint.setIndexY(inY + 1);
                // System.out.println("GORA");
            }
        }

        ///////////////////////ROG UPDATE//////////////////////////////
        if (!colision && colisionType != 5 && rightSideColision && upSideColision) {
            //System.out.println("PRAWY GORNY ROG");
            if (matrix[inX - 1][inY + 1] == 1) {
                colisionPoint.setDirectionY(-directionY);
                colisionPoint.setDirectionX(-directionX);
//                colisionPoint.setColision(true);
                colision = true;
                colisionType = 5;
                colisionPoint.setIndexX(inX - 1);
                colisionPoint.setIndexY(inY + 1);
            }
        } else if (!colision && colisionType != 6 && rightSideColision && SideColision) {
            //System.out.println("PRAWY DOLNY ROG");
            if (matrix[inX - 1][inY - 1] == 1) {
                colisionPoint.setDirectionY(-directionY);
                colisionPoint.setDirectionX(-directionX);
//                colisionPoint.setColision(true);
                colision = true;
                colisionType = 6;
                colisionPoint.setIndexX(inX - 1);
                colisionPoint.setIndexY(inY - 1);
            }
        } else if (!colision && colisionType != 7 && leftSideColision && upSideColision) {
            //System.out.println("LEWY GORNY ROG");
            if (matrix[inX + 1][inY + 1] == 1) {
                colisionPoint.setDirectionY(-directionY);
                colisionPoint.setDirectionX(-directionX);
//                colisionPoint.setColision(true);
                colision = true;
                colisionType = 7;
                colisionPoint.setIndexX(inX + 1);
                colisionPoint.setIndexY(inY + 1);
            }

        } else if (!colision && colisionType != 8 && leftSideColision && SideColision) {
            //System.out.println("LEWY DOLNY ROG");
            if (matrix[inX + 1][inY - 1] == 1) {
                colisionPoint.setDirectionY(-directionY);
                colisionPoint.setDirectionX(-directionX);
//                colisionPoint.setColision(true);
                colision = true;
                colisionType = 8;
                colisionPoint.setIndexX(inX + 1);
                colisionPoint.setIndexY(inY - 1);
            }
        }

        colisionPoint.setColisionType(colisionType);
        colisionPoint.setColision(colision);

        return colisionPoint;
    }

}
