package com.oddfactory.uros;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

final class UiFont {
    private static Typeface light,bold;
    static synchronized Typeface light(Context c) { if(light==null)light=Typeface.createFromAsset(c.getAssets(),"fonts/Maplestory-Light.ttf");return light; }
    static synchronized Typeface bold(Context c) { if(bold==null)bold=Typeface.createFromAsset(c.getAssets(),"fonts/Maplestory-Bold.ttf");return bold; }
    static void apply(View view) {
        if(view instanceof TextView) ((TextView)view).setTypeface(light(view.getContext()));
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)apply(((ViewGroup)view).getChildAt(i));
    }
}
