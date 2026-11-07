package com.oddfactory.uros;

/** Fast, monotone 28ms response, with a continuous finite endpoint at 100ms. */
final class Motion {
    static final double RATE = 36, SETTLE_SECONDS = .100;
    private static final double END_DECAY = Math.exp(-RATE * SETTLE_SECONDS);
    private double target, start, current, elapsed = SETTLE_SECONDS;

    static double clamp(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    void setTarget(double value) {
        double next = clamp(value);
        if (target == next) return;
        start = current;
        target = next;
        elapsed = 0;
    }
    void snap(double value) { target = start = current = clamp(value); elapsed = SETTLE_SECONDS; }
    double target() { return target; }
    double current() { return current; }
    void step(double dt) {
        if (!(dt > 0) || !Double.isFinite(dt) || settled()) return;
        elapsed = Math.min(SETTLE_SECONDS, elapsed + dt);
        double remainder = (Math.exp(-RATE * elapsed) - END_DECAY) / (1 - END_DECAY);
        current = elapsed >= SETTLE_SECONDS ? target : target + (start - target) * remainder;
    }
    boolean settled() { return current == target; }
}
