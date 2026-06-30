// pl/mzebrows/shoots/app/GameplayOptions.java
package pl.mzebrows.shoots.config;

import lombok.Getter;
import lombok.Setter;

import java.util.regex.Pattern;

/**
 * Live, in-memory gameplay tunables edited in the menu's GAMEPLAY OPTIONS screen. Seeded from the loaded
 * {@link GameConfig} (and {@link OnlineConfig}) and clamped to {@link GameplayLimits}. There is no save
 * button: every change is held here and applied the next time a match is built ({@link #applyTo}), and the
 * host's values are propagated to clients when an online match starts.
 *
 * <p>The power shot has no dedicated speed/bounce options: both stay proportional to the disc
 * ({@code power.speedFactor} / {@code power.maxBouncesFactor}), so raising the base disc values raises the
 * power-shot ones with them.
 */
@Getter
public final class GameplayOptions {

    /** Matches a dotted-quad IPv4 address with each octet in 0..255 (used by the host-IP field). */
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private final GameplayLimits limits;

    private int roundTimeSeconds;
    private int roundLimit;
    private int maxDiscBounces;
    private double discSpeed;
    private int maxLaserBounces;
    private double disruptionSeconds;
    private double graceSeconds;
    /** Free-text host IP being edited; may be partial/invalid while typing -- see {@link #isHostIpValid()}. */
    @Setter private String hostIp;
    private int hostPort;

    /** Builds the live options from the loaded game/online config defaults, clamped to {@code limits}. */
    public GameplayOptions(GameConfig config, OnlineConfig online, GameplayLimits limits) {
        this.limits = limits;
        this.roundTimeSeconds = limits.roundTime().clamp(config.round().roundTimeSeconds());
        this.roundLimit = limits.roundLimit().clamp(config.round().roundLimit());
        this.maxDiscBounces = limits.discBounces().clamp(config.disc().maxBounces());
        this.discSpeed = limits.discSpeed().clamp(config.disc().moveSpeed());
        this.maxLaserBounces = limits.laserBounces().clamp(config.disc().laserMaxBounces());
        this.disruptionSeconds = limits.disruptionSeconds().clamp(config.disruption().durationSeconds());
        this.graceSeconds = limits.graceSeconds().clamp(config.disruption().graceSeconds());
        this.hostIp = online.host();
        this.hostPort = clampPort(online.port());
    }

    // --- edit helpers (dir = -1 / +1): step within the cap range, clamped (no wrap) -----------------

    public void adjustRoundTime(int dir) {
        roundTimeSeconds = limits.roundTime().clamp(roundTimeSeconds + dir * limits.roundTime().step());
    }

    public void adjustRoundLimit(int dir) {
        roundLimit = limits.roundLimit().clamp(roundLimit + dir * limits.roundLimit().step());
    }

    public void adjustDiscBounces(int dir) {
        maxDiscBounces = limits.discBounces().clamp(maxDiscBounces + dir * limits.discBounces().step());
    }

    public void adjustDiscSpeed(int dir) {
        discSpeed = round2(limits.discSpeed().clamp(discSpeed + dir * limits.discSpeed().step()));
    }

    public void adjustLaserBounces(int dir) {
        maxLaserBounces = limits.laserBounces().clamp(maxLaserBounces + dir * limits.laserBounces().step());
    }

    public void adjustDisruptionSeconds(int dir) {
        disruptionSeconds = round2(limits.disruptionSeconds().clamp(disruptionSeconds + dir * limits.disruptionSeconds().step()));
    }

    public void adjustGraceSeconds(int dir) {
        graceSeconds = round2(limits.graceSeconds().clamp(graceSeconds + dir * limits.graceSeconds().step()));
    }

    public void adjustPort(int dir) {
        hostPort = clampPort(hostPort + dir);
    }

    public void setHostPort(int port) {
        this.hostPort = clampPort(port);
    }

    private int clampPort(int p) {
        return Math.max(limits.minPort(), Math.min(limits.maxPort(), p));
    }

    /** Whether the current {@link #hostIp} parses as a valid dotted-quad IPv4 address. */
    public boolean isHostIpValid() {
        return hostIp != null && IPV4.matcher(hostIp).matches();
    }

    /**
     * Overlays these gameplay options onto a base {@link GameConfig}, returning a new config with the disc
     * speed/bounces/laser, base-disruption timings, and round time/limit applied. The power-shot speed and
     * bounce factors and every other tunable are preserved (power stats stay proportional to the new disc).
     */
    public GameConfig applyTo(GameConfig base) {
        DiscConfig d = base.disc();
        DiscConfig disc = new DiscConfig(d.bigRadius(), d.smallRadius(), discSpeed, maxDiscBounces,
                d.maxPerPlayer(), d.maxPerShot(), maxLaserBounces, d.bounceSpeedGain(), d.maxSpeedFactor(),
                d.laserBounceAlphaFalloff());
        DisruptionConfig dis = new DisruptionConfig(base.disruption().enabled(), disruptionSeconds, graceSeconds);
        RoundConfig r = base.round();
        RoundConfig round = new RoundConfig(roundTimeSeconds, roundLimit, r.roundEndDelay(), r.animationTime());
        return base.withDisc(disc).withDisruption(dis).withRound(round);
    }

    /** Copies another instance's values into this one (used to adopt a host's options on a client). */
    public void copyFrom(GameplayOptions other) {
        this.roundTimeSeconds = limits.roundTime().clamp(other.roundTimeSeconds);
        this.roundLimit = limits.roundLimit().clamp(other.roundLimit);
        this.maxDiscBounces = limits.discBounces().clamp(other.maxDiscBounces);
        this.discSpeed = limits.discSpeed().clamp(other.discSpeed);
        this.maxLaserBounces = limits.laserBounces().clamp(other.maxLaserBounces);
        this.disruptionSeconds = limits.disruptionSeconds().clamp(other.disruptionSeconds);
        this.graceSeconds = limits.graceSeconds().clamp(other.graceSeconds);
        this.hostIp = other.hostIp;
        this.hostPort = clampPort(other.hostPort);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
