package com.oddfactory.uros;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UrosAccessibilityService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener, DisplayManager.DisplayListener {
    static volatile boolean connected;
    static volatile int status;
    private static UrosAccessibilityService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private WindowManager windows;
    private ControlAssets pillowAssets, urosAssets;
    private ControlOverlay pillow, uros;
    private boolean registered, destroyed;
    private final BroadcastReceiver screen=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) { refresh(); }
    };
    @Override protected void onServiceConnected() {
        super.onServiceConnected(); instance=this; connected=true; windows=getSystemService(WindowManager.class);
        AccessibilityServiceInfo info=getServiceInfo();
        if(info!=null) { info.eventTypes=0; info.flags=0; setServiceInfo(info); }
        if(!registered) {
            Preferences.get(this).registerOnSharedPreferenceChangeListener(this);
            getSystemService(DisplayManager.class).registerDisplayListener(this,handler);
            IntentFilter filter=new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_USER_PRESENT);
            if(Build.VERSION.SDK_INT>=33) registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screen,filter);
            registered=true;
        }
        loader.execute(()->{
            try {
                ControlAssets left=new ControlAssets(this,true), right=new ControlAssets(this,false);
                handler.post(()->{ if(!destroyed) { pillowAssets=left; urosAssets=right; applyRunning(); } });
            } catch(Exception e) { handler.post(()->{ if(!destroyed) status=2; }); }
            finally { loader.shutdown(); }
        });
    }
    static void showIfConnected() {
        UrosAccessibilityService service=instance;
        if(service!=null) service.handler.post(()->{
            if(!service.destroyed && Preferences.running(service)) {
                if(service.pillow!=null)service.pillow.cancelGesture();
                if(service.uros!=null)service.uros.cancelGesture();
                service.applyRunning();
                if(service.pillow!=null) service.pillow.syncWindow();
                if(service.uros!=null) service.uros.syncWindow();
            }
        });
    }
    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT; p.setFitInsetsTypes(0);
        p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.windowAnimations=0;
        return p;
    }
    private void applyRunning() {
        if(!Preferences.running(this)) { remove(); status=0; return; }
        if(pillowAssets==null) return;
        try {
            if(pillow==null) {
                WindowManager.LayoutParams lp=params(), rp=params();
                pillow=new ControlOverlay(this,pillowAssets,windows,lp); uros=new ControlOverlay(this,urosAssets,windows,rp);
                // WindowManager copies LayoutParams at addView. Set the real bounds before that copy.
                refresh();
                windows.addView(pillow,lp); windows.addView(uros,rp);
            }
            status=1; refresh();
        } catch(RuntimeException e) { remove(); status=3; }
    }
    private void refresh() {
        if(pillow==null) return;
        WindowMetrics metrics=windows.getCurrentWindowMetrics(); Rect b=new Rect(metrics.getBounds());
        Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        b.offsetTo(0,0); b.left+=insets.left; b.top+=insets.top; b.right-=insets.right; b.bottom-=insets.bottom;
        int size=Math.max(64,Math.min(160,Preferences.get(this).getInt("controlSize",104)));
        // Keep both sprites within even a small multi-window/display viewport.
        float density=getResources().getDisplayMetrics().density;
        size=Math.min(size,Math.max(1,(int)Math.min(b.height()/density/2.3,b.width()/density/2.8)));
        pillow.layout(b,size); uros.layout(b,size);
        boolean visible=getSystemService(PowerManager.class).isInteractive()&&!getSystemService(KeyguardManager.class).isKeyguardLocked();
        pillow.setActive(visible); uros.setActive(visible);
    }
    private void remove() {
        for(ControlOverlay view:new ControlOverlay[]{pillow,uros}) if(view!=null) { view.close(); if(view.isAttachedToWindow()) windows.removeViewImmediate(view); }
        pillow=null; uros=null;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* No app content or scrolling is read. */ }
    @Override public void onInterrupt() { remove(); status=0; }
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key) { if("running".equals(key)) applyRunning(); else if(key==null||(!key.startsWith("standby")&&!key.startsWith("place")))refresh(); }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); handler.post(this::refresh); }
    @Override public void onDisplayAdded(int id) { handler.post(this::refresh); }
    @Override public void onDisplayRemoved(int id) { handler.post(this::refresh); }
    @Override public void onDisplayChanged(int id) { handler.post(this::refresh); }
    @Override public void onDestroy() {
        destroyed=true; connected=false; if(instance==this)instance=null; remove(); handler.removeCallbacksAndMessages(null); loader.shutdownNow();
        if(registered) { unregisterReceiver(screen); Preferences.get(this).unregisterOnSharedPreferenceChangeListener(this); getSystemService(DisplayManager.class).unregisterDisplayListener(this); }
        super.onDestroy();
    }
    @Override protected void dump(FileDescriptor fd,PrintWriter out,String[] args) {
        out.println("controls version=2 status="+status+" connected="+connected+" running="+Preferences.running(this));
        out.println("brightnessPermission="+android.provider.Settings.System.canWrite(this));
        out.println("left="+(pillow==null?"absent":pillow.value())+" right="+(uros==null?"absent":uros.value()));
        if(pillow!=null) out.println("leftBounds "+pillow.geometry());
        if(uros!=null) out.println("rightBounds "+uros.geometry());
    }
}
