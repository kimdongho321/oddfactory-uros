package com.oddfactory.uros;

/** Computes the elbow position for the supporting arm. */
final class RigPose {
    static void solveArm(double sx, double sy, double wx, double wy,
                         double l1, double l2, int side, float[] elbow) {
        double dx = wx - sx, dy = wy - sy;
        double real = Math.hypot(dx, dy);
        if (real == 0) real = 1;
        double expand = Math.max(1, real / (l1 + l2 - 2));
        l1 *= expand; l2 *= expand;
        double d = Math.min(real, l1 + l2 - .1), ux = dx / real, uy = dy / real;
        double a = (l1*l1 - l2*l2 + d*d) / (2*d), h = Math.sqrt(Math.max(0, l1*l1 - a*a));
        elbow[0] = (float) (sx + ux*a + uy*h*side);
        elbow[1] = (float) (sy + uy*a - ux*h*side);
    }
}
