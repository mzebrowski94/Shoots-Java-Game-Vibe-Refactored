// src/main/java/pl/mzebrows/shoots/entity/BounceMovementStrategy.java
package pl.mzebrows.shoots.entity;

/**
 * Straight-line, angle-driven integration matching the legacy disc convention
 * (X uses {@code sin(-angle)}, Y uses {@code cos(-angle)}); reflection is applied separately by the
 * collider flipping {@code directionX}/{@code directionY}. Stateless and therefore shareable.
 */
public final class BounceMovementStrategy implements MovementStrategy {

    @Override
    public void move(Entity entity) {
        double radians = Math.toRadians(-entity.getAngle());
        double speed = entity.getMoveSpeed();
        entity.setX(entity.getX() + entity.getDirectionX() * speed * Math.sin(radians));
        entity.setY(entity.getY() + entity.getDirectionY() * speed * Math.cos(radians));
    }
}
