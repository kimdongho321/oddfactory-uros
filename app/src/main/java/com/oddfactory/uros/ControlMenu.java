package com.oddfactory.uros;

import android.app.Dialog;
import android.content.Context;
import android.view.WindowManager;
import android.widget.*;

/** Real settings remain reachable while another app is in front. */
final class ControlMenu {
    static Dialog show(Context c,boolean pillow,boolean sleeping,Runnable toggle,Runnable reset) {
        Dialog dialog=new Dialog(c);
        LinearLayout page=new LinearLayout(c);page.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(20*c.getResources().getDisplayMetrics().density);page.setPadding(pad,pad,pad,pad);
        ScrollView scroll=new ScrollView(c);scroll.addView(page);
        TextView title=new TextView(c);title.setText(pillow?"우로롱 · 밝기":"우로스 · 볼륨");title.setTextSize(23);page.addView(title);
        add(c,page,sleeping?"깨우기":"대기 상태",()->{dialog.dismiss();toggle.run();});
        LinearLayout settings=new LinearLayout(c);settings.setOrientation(LinearLayout.VERTICAL);settings.setVisibility(android.view.View.GONE);
        add(c,page,"설정",()->settings.setVisibility(settings.getVisibility()==android.view.View.VISIBLE?android.view.View.GONE:android.view.View.VISIBLE));
        page.addView(settings);
        slider(c,settings,"크기", "controlSize",64,160,104," dp");
        slider(c,settings,"최대 길이", "controlMaxPercent",100,240,165,"%");
        slider(c,settings,"기본 세로 위치", "controlPosition",30,85,60,"%");
        add(c,settings,"이 캐릭터 위치를 기본으로",()->{reset.run();dialog.dismiss();});
        add(c,page,"종료",()->{dialog.dismiss();Preferences.get(c).edit().putBoolean("running",false).apply();});
        add(c,page,"닫기",dialog::dismiss);
        UiFont.apply(page);title.setTypeface(UiFont.bold(c));
        dialog.setContentView(scroll);
        android.view.Window window=dialog.getWindow();
        if(window==null)throw new IllegalStateException("menu window");
        window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        dialog.show();
        WindowManager wm=c.getSystemService(WindowManager.class);
        android.graphics.Rect bounds=wm.getCurrentWindowMetrics().getBounds();
        android.graphics.Insets insets=wm.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
        window.setLayout(Math.max(1,Math.min(Math.round(360*c.getResources().getDisplayMetrics().density),bounds.width()-insets.left-insets.right-pad)),WindowManager.LayoutParams.WRAP_CONTENT);
        return dialog;
    }
    private static void add(Context c,LinearLayout page,String name,Runnable action) {
        Button b=new Button(c);b.setText(name);b.setAllCaps(false);b.setMinHeight(Math.round(48*c.getResources().getDisplayMetrics().density));b.setOnClickListener(v->action.run());page.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    private static void slider(Context c,LinearLayout page,String name,String key,int min,int max,int fallback,String suffix) {
        TextView label=new TextView(c);page.addView(label);
        SeekBar seek=new SeekBar(c);seek.setMin(min);seek.setMax(max);seek.setProgress(Preferences.get(c).getInt(key,fallback));
        label.setText(name+" · "+seek.getProgress()+suffix);seek.setContentDescription(label.getText());page.addView(seek,new LinearLayout.LayoutParams(-1,Math.round(48*c.getResources().getDisplayMetrics().density)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int value,boolean user){label.setText(name+" · "+value+suffix);seek.setContentDescription(label.getText());if(user)Preferences.get(c).edit().putInt(key,value).apply();}
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
    }
}
