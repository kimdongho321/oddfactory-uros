package com.oddfactory.uros;

import android.content.Context;
import android.content.SharedPreferences;

final class Preferences {
    static SharedPreferences get(Context context) { return context.getSharedPreferences("uros", Context.MODE_PRIVATE); }
    static boolean running(Context context) { return get(context).getBoolean("running", false); }
    static int size(Context context) { return Math.max(32, Math.min(160, get(context).getInt("size", 64))); }
    static int responseScreens(Context context) { return Math.max(3, Math.min(20, get(context).getInt("responseScreens", 8))); }
    static float x(Context context) { return (float) Motion.clamp(get(context).getInt("x", 100) / 100.0); }
    static float y(Context context) { return (float) Motion.clamp(get(context).getInt("y", 100) / 100.0); }
}
