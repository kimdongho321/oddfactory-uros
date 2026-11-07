package com.oddfactory.uros;

/** Value travel is independent of the sprite, display size and folding state. */
final class ControlMath {
    static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    static double drag(double start, double deltaY, double travel) {
        return clamp(start - deltaY / Math.max(1, travel));
    }
    static int level(double value, int min, int max) { return min + (int)Math.round(clamp(value) * (max-min)); }
}
