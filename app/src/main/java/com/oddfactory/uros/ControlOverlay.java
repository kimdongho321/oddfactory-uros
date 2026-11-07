package com.oddfactory.uros;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.media.AudioManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewConfiguration;
import android.widget.Toast;

/** A small touchable window at each edge; no global gesture interception. */
@SuppressLint("ViewConstructor")
final class ControlOverlay extends View {
    final ControlAssets a;
    private final WindowManager windows;
    private final WindowManager.LayoutParams params;
    private final AudioManager audio;
    private final Paint pixel=new Paint(), cloth=new Paint(Paint.FILTER_BITMAP_FLAG), stroke=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect source=new Rect(), safe=new Rect();
    private final RectF dest=new RectF();
    private final Path arm=new Path();
    private final float[] elbow=new float[2];
    private float density;
    private double target, current, start;
    private float startY, scale, maximumLift,originX,originY;
    private int anchor;
    private long lastTick, lastRead, labelUntil;
    private int pointer=-1, size;
    private int lastStretch=-1,lastPosition=-1;
    private boolean active, attached, dragged;
    private final StandbyMotion standby=new StandbyMotion();
    private float startX;
    private final TapSequence taps=new TapSequence();
    private final OpaqueInput inputs;
    private final ControlGesture gesture=new ControlGesture();
    private android.app.Dialog menu;
    private boolean hitDrawing,hitDirty=true;
    private Bitmap hitBitmap;
    private Canvas hitCanvas;
    private int[] hitPixels;
    private float placedX=Float.NaN,grabX;
    private float lastPlaceX=Float.NaN,lastPlaceY=Float.NaN;
    private int grabAnchor;
    private long downAt;
    private final Runnable tick=this::tick;
    private final Runnable syncWindow=this::syncWindow;
    private final Runnable hold=()->{if(pointer>=0){gesture.hold(SystemClock.uptimeMillis());clearTaps();}};
    private final Runnable resolveTaps=()->{if(active&&pointer<0&&taps.take()==2)toggleStandby();};
    private final Runnable hitTask=this::rebuildInput;
    ControlOverlay(Context c, ControlAssets assets, WindowManager wm, WindowManager.LayoutParams layout) {
        super(c); a=assets; windows=wm; params=layout; audio=c.getSystemService(AudioManager.class);
        inputs=new OpaqueInput(c,wm,layout,this::onTouchEvent);
        density=getResources().getDisplayMetrics().density;
        pixel.setFilterBitmap(false); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER); text.setTextSize(12*density); text.setTypeface(UiFont.bold(c));
        target=current=read(); setContentDescription(a.pillow?"우로롱 밝기, 위로 늘리면 밝아집니다":"우로스 미디어 볼륨, 위로 늘리면 커집니다");
        if(Preferences.get(c).getBoolean(standbyKey(),false)){standby.state="sleep";standby.sample(0);}
    }
    private String standbyKey(){return a.pillow?"standbyPillow":"standbyUros";}
    private String placeKey(){return a.pillow?"placePillow":"placeUros";}
    double value() { return target; }
    String geometry() { return "fixedCanvas=true opaqueInputWindows="+inputs.count()+" x="+params.x+" y="+params.y+" width="+params.width+" height="+params.height+" anchor="+anchor+" maxLift="+maximumLift+" standby="+standby.state+" visible="+(getVisibility()==VISIBLE)+" input{"+inputs.diagnostics()+"}"; }
    private double read() {
        try {
            if(a.pillow) return ControlMath.clamp((Settings.System.getInt(getContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,153)-1)/254.0);
            return audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(double)Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch(RuntimeException e) { return target; }
    }
    private void write(double value) {
        if(standby.blocked())return;
        try {
            if(a.pillow) {
                if(!Settings.System.canWrite(getContext())) { Toast.makeText(getContext(),"우로스 앱에서 ‘밝기 변경 허용’을 먼저 켜 주세요.",Toast.LENGTH_SHORT).show(); pointer=-1; return; }
                Settings.System.putInt(getContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(getContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,ControlMath.level(value,1,255));
            } else {
                int level=ControlMath.level(value,0,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,level,0);
            }
            target=read();
        } catch(SecurityException e) { pointer=-1; Toast.makeText(getContext(),"시스템에서 값 변경을 허용하지 않았습니다.",Toast.LENGTH_SHORT).show(); target=read(); }
    }
    void layout(Rect bounds,int baseSize) {
        int position=Math.max(30,Math.min(85,Preferences.get(getContext()).getInt("controlPosition",60)));
        int stretch=Math.max(100,Math.min(240,Preferences.get(getContext()).getInt("controlMaxPercent",165)));
        android.content.SharedPreferences prefs=Preferences.get(getContext());
        float requestX=prefs.contains(placeKey()+"X")?prefs.getFloat(placeKey()+"X",Float.NaN):Float.NaN;
        float requestY=prefs.contains(placeKey()+"Y")?prefs.getFloat(placeKey()+"Y",Float.NaN):Float.NaN;
        // Display callbacks also report brightness/refresh-rate changes. They are
        // not geometry changes and must not tear down the current touch target.
        if(safe.equals(bounds)&&size==baseSize&&density==getResources().getDisplayMetrics().density&&lastStretch==stretch&&lastPosition==position&&Float.compare(lastPlaceX,requestX)==0&&Float.compare(lastPlaceY,requestY)==0)return;
        cancelInput();lastPlaceX=requestX;lastPlaceY=requestY;
        lastStretch=stretch;lastPosition=position;
        density=getResources().getDisplayMetrics().density;text.setTextSize(12*density);
        safe.set(bounds); size=baseSize; scale=size*density/a.baseline;
        anchor=safe.top+Math.round(safe.height()*position/100f);
        if(prefs.contains(placeKey()+"Y"))anchor=safe.top+Math.round(safe.height()*prefs.getFloat(placeKey()+"Y",position/100f));
        anchor=Math.max(safe.top+Math.round((a.height-a.head)*scale+24*density),Math.min(safe.bottom,anchor));
        placedX=prefs.contains(placeKey()+"X")?safe.left+Math.max(0,safe.width()-a.width*scale)*prefs.getFloat(placeKey()+"X",a.pillow?0:1):Float.NaN;
        float requested=(stretch/100f-1)*a.baseline;
        maximumLift=Math.max(0,Math.min(requested,(anchor-safe.top-24*density)/scale-(a.height-a.head)));
        place();
    }
    private void place() {
        if(scale<=0||safe.isEmpty())return;
        ControlGeometry g=ControlGeometry.create(a.pillow,a.width,a.height,a.head,scale,density,maximumLift,anchor,safe.left,safe.top,safe.right,safe.bottom,current,standby.state.equals("sleep"),a.rest);
        if(Float.isFinite(placedX))g.x+=Math.round(placedX-(g.x+g.originX));
        originX=g.originX;originY=g.originY;
        boolean changed=params.x!=g.x||params.y!=g.y||params.width!=g.width||params.height!=g.height;
        params.x=g.x;params.y=g.y;params.width=g.width;params.height=g.height;
        if(attached&&changed)windows.updateViewLayout(this,params);
        hitDirty=true;
        if(attached&&active&&pointer<0){removeCallbacks(hitTask);post(hitTask);}
    }
    void setActive(boolean yes) {
        if(active==yes&&yes)return;
        active=yes;setVisibility(yes?VISIBLE:GONE);removeCallbacks(tick);
        if(yes) { hitDirty=true;lastTick=SystemClock.uptimeMillis();post(tick);post(hitTask); }
        else {cancelInput();inputs.clear();hitDirty=true;removeCallbacks(hitTask);if(menu!=null)menu.dismiss();menu=null;}
    }
    void syncWindow() {
        if(!attached || scale<=0 || safe.isEmpty())return;
        place();
        // Also sync unchanged bounds: the root may still hold its original copied parameters.
        windows.updateViewLayout(this,params);
        hitDirty=true;post(hitTask);
        invalidate();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached=true; post(syncWindow); }
    @Override protected void onDetachedFromWindow() { close();attached=false;super.onDetachedFromWindow(); }
    void close() { setActive(false);removeCallbacks(tick);removeCallbacks(syncWindow);removeCallbacks(hitTask);removeCallbacks(hold);inputs.clear();if(hitBitmap!=null){hitBitmap.recycle();hitBitmap=null;hitCanvas=null;hitPixels=null;} }
    private void clearTaps(){removeCallbacks(resolveTaps);taps.clear();}
    private void cancelInput(){pointer=-1;inputs.releaseOwner();clearTaps();gesture.cancel();removeCallbacks(hold);labelUntil=0;}
    void cancelGesture(){cancelInput();}
    private void rebuildInput(){
        if(!attached||!active||pointer>=0||!hitDirty)return;
        if(standby.moving()){inputs.clear();return;}
        if(hitBitmap==null||hitBitmap.getWidth()!=params.width||hitBitmap.getHeight()!=params.height){
            if(hitBitmap!=null)hitBitmap.recycle();
            hitBitmap=Bitmap.createBitmap(params.width,params.height,Bitmap.Config.ARGB_8888);
            hitCanvas=new Canvas(hitBitmap);hitPixels=new int[params.width*params.height];
        }
        hitBitmap.eraseColor(Color.TRANSPARENT);
        hitDrawing=true;try{renderCharacter(hitCanvas);}finally{hitDrawing=false;}
        hitBitmap.getPixels(hitPixels,0,params.width,0,0,params.width,params.height);
        // A prone face/body is only a few grid cells high. Use a finer opaque
        // grid there; keep the transparent contour and outside apps untouched.
        int hitCell=Math.max(1,Math.round((standby.state.equals("sleep")?1:4)*density));
        try{if(inputs.rebuild(OpaqueCells.rectangles(hitPixels,params.width,params.height,hitCell),params.x,params.y,safe))hitDirty=false;}
        catch(RuntimeException error){inputs.clear();UrosAccessibilityService.status=3;hitDirty=false;}
    }
    private void tick() {
        if(!active) return;
        long now=SystemClock.uptimeMillis();
        String previous=standby.state;standby.sample(now);
        if(!previous.equals(standby.state))place();
        if(pointer<0 && now-lastRead>=250) { target=read(); lastRead=now; }
        double dt=Math.min(.1,Math.max(0,(now-lastTick)/1000.0)); lastTick=now;
        double old=current; current+=(target-current)*(1-Math.exp(-dt/.045)); if(Math.abs(target-current)<.0004) current=target;
        if(Math.abs(old-current)>.000001) place();
        invalidate();
        long delay=(standby.moving()||current!=target || pointer>=0)?16:250;
        if(labelUntil>now) delay=Math.min(delay,labelUntil-now);
        postDelayed(tick,Math.max(1,delay));
    }
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if(!active||pointer>=0||standby.moving()||(menu!=null&&menu.isShowing()))return true;
                startX=event.getRawX();downAt=SystemClock.uptimeMillis();
                removeCallbacks(resolveTaps);taps.down(startX,event.getRawY(),downAt,ViewConfiguration.getDoubleTapTimeout(),ViewConfiguration.get(getContext()).getScaledDoubleTapSlop());
                pointer=event.getPointerId(0); startY=event.getRawY(); start=read();target=start;dragged=false;
                grabX=params.x+originX;grabAnchor=anchor;
                gesture.begin(startX,startY,downAt,ViewConfiguration.get(getContext()).getScaledTouchSlop(),ViewConfiguration.getLongPressTimeout());
                removeCallbacks(hold);postDelayed(hold,ViewConfiguration.getLongPressTimeout());
                removeCallbacks(tick); post(tick); return true;
            case MotionEvent.ACTION_MOVE:
                int index=event.findPointerIndex(pointer); if(index<0) return true;
                float px=event.getRawX(index),py=event.getRawY(index),dy=py-startY;
                ControlGesture.Mode mode=gesture.move(px,py,SystemClock.uptimeMillis());
                if(mode==ControlGesture.Mode.PRESSED)return true;
                dragged=true;clearTaps();removeCallbacks(hold);
                if(mode==ControlGesture.Mode.POSITION){
                    placedX=Math.max(safe.left,Math.min(safe.right-a.width*scale,grabX+px-startX));
                    int minimum=safe.top+Math.round((a.height+Math.max(0,maximumLift-a.head))*scale+24*density);
                    anchor=Math.max(minimum,Math.min(safe.bottom,grabAnchor+Math.round(dy)));place();invalidate();return true;
                }
                if(standby.blocked())return true;
                write(ControlMath.drag(start,dy,180*density)); labelUntil=SystemClock.uptimeMillis()+1000; return true;
            case MotionEvent.ACTION_UP:
                long now=SystemClock.uptimeMillis();
                removeCallbacks(hold);
                boolean movingPosition=gesture.mode==ControlGesture.Mode.POSITION;
                if(pointer>=0&&movingPosition&&dragged){
                    lastPlaceX=(float)ControlMath.clamp((placedX-safe.left)/Math.max(1,safe.width()-a.width*scale));
                    lastPlaceY=(anchor-safe.top)/(float)Math.max(1,safe.height());
                    Preferences.get(getContext()).edit().putFloat(placeKey()+"X",lastPlaceX).putFloat(placeKey()+"Y",lastPlaceY).apply();
                }
                if(pointer>=0&&!dragged&&gesture.mode==ControlGesture.Mode.PRESSED&&now-downAt<ViewConfiguration.getLongPressTimeout()){
                    performClick();
                    if(taps.up(startX,startY,now)==3){cancelInput();showMenu();}
                    else postDelayed(resolveTaps,ViewConfiguration.getDoubleTapTimeout());
                }else clearTaps();
                pointer=-1;gesture.cancel();if(hitDirty)post(hitTask);labelUntil=movingPosition||standby.blocked()?0:now+900; return true;
            case MotionEvent.ACTION_CANCEL: cancelInput();if(hitDirty)post(hitTask);return true;
            case MotionEvent.ACTION_POINTER_DOWN: cancelInput();return true;
            case MotionEvent.ACTION_POINTER_UP:
                if(event.getPointerId(event.getActionIndex())==pointer){cancelInput();if(hitDirty)post(hitTask);}return true;
            default:return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private void toggleStandby(){
        if(!active||standby.moving())return;
        clearTaps();standby.stretchDistance=Math.max(0,current*maximumLift/(a.baseline*.75));
        if(standby.toggle(SystemClock.uptimeMillis())){
            Preferences.get(getContext()).edit().putBoolean(standbyKey(),standby.state.equals("sleeping")).apply();
            place();removeCallbacks(tick);post(tick);
        }
    }
    private void showMenu(){
        if(menu!=null)menu.dismiss();
        menu=ControlMenu.show(getContext(),a.pillow,standby.state.equals("sleep"),this::toggleStandby,()->{Preferences.get(getContext()).edit().remove(placeKey()+"X").remove(placeKey()+"Y").apply();UrosAccessibilityService.showIfConnected();});
    }
    private void piece(Canvas c,Bitmap bitmap,int y,int end,float top,float bottom,Paint paint) {
        source.set(0,y,a.width,end); dest.set(0,top,a.width,bottom); c.drawBitmap(bitmap,source,dest,paint);
    }
    private void arm(Canvas c,float sx,float sy,float ex,float ey,float wx,float wy) {
        arm.rewind(); arm.moveTo(sx,sy); arm.lineTo(ex,ey); arm.lineTo(wx,wy);
        stroke.setColor(a.pillow?0xff96701c:0xff967322); stroke.setStrokeWidth(a.pillow?30:33); c.drawPath(arm,stroke);
        stroke.setColor(0xfffffefa); stroke.setStrokeWidth(a.pillow?16:18); c.drawPath(arm,stroke);
    }
    private void solvedArm(Canvas c,float sx,float sy,float wx,float wy,double l1,double l2,int side) {
        RigPose.solveArm(sx,sy,wx,wy,l1,l2,side,elbow); arm(c,sx,sy,elbow[0],elbow[1],wx,wy);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);renderCharacter(c);
    }
    private void renderCharacter(Canvas c) {
        float lift=Math.round(current*maximumLift*scale)/scale;
        c.save(); c.translate(originX,originY); c.scale(scale,scale);
        if(standby.blocked()){drawStandby(c,lift);c.restore();return;}
        c.drawBitmap(a.rear,0,0,pixel);
        if(a.pillow) {
            arm(c,350,548,230+55*(float)current,760-580*(float)current,438,715-lift);
            arm(c,371,558,610+90*(float)current,795-550*(float)current,831,716-lift);
        } else {
            solvedArm(c,800,521,420,743-lift,280,285,-1);
            solvedArm(c,850,514,670,741-lift,190+current*65,320+current*95,1);
        }
        // Two source rows overlap rigid pieces to avoid fractional-pixel seams.
        float band=a.lower-a.upper, ratio=(band+lift)/band;
        piece(c,a.front,a.upper-2,a.lower+2,a.upper-lift-2*ratio,a.lower+2*ratio,cloth);
        piece(c,a.front,0,a.upper,-lift,a.upper-lift,pixel);
        piece(c,a.front,a.lower,a.height,a.lower,a.height,pixel);
        c.drawBitmap(a.left,0,-lift,pixel); c.drawBitmap(a.right,0,-lift,pixel);
        c.restore();
        if(!hitDrawing&&labelUntil>SystemClock.uptimeMillis()) {
            float labelY=Math.max(0,originY+(a.head-lift)*scale-24*density);c.save();c.translate(originX,labelY);text.setColor(0xdd402735); c.drawRoundRect(1,0,a.width*scale-1,22*density,8*density,8*density,text);
            text.setColor(Color.WHITE); c.drawText((a.pillow?"밝기 ":"볼륨 ")+Math.round(target*100)+"%",a.width*scale/2f,16*density,text);c.restore();
        }
    }
    private void drawStandby(Canvas c,float rawLift){
        float lift=rawLift*(float)standby.extension;
        float[] pose=standby.frame>=0?a.standbyPoses[standby.frame]:new float[]{1,0,-lift};
        if(standby.holder>0){int save=c.saveLayerAlpha(null,(int)Math.round(255*standby.holder));c.drawBitmap(a.rear,0,0,pixel);
            if(a.pillow){arm(c,350,548,230+55*(float)current,760-580*(float)(current*standby.extension),438*pose[0]+pose[1],715*pose[0]+pose[2]);arm(c,371,558,610+90*(float)current,795-550*(float)(current*standby.extension),831*pose[0]+pose[1],716*pose[0]+pose[2]);}
            else{solvedArm(c,800,521,420*pose[0]+pose[1],743*pose[0]+pose[2],280,285,-1);solvedArm(c,850,514,670*pose[0]+pose[1],741*pose[0]+pose[2],190+current*65,320+current*95,1);}c.restoreToCount(save);}
        if(standby.frame>=0){dest.set(0,0,a.width,a.height);c.drawBitmap(a.standbyFrames[standby.frame],null,dest,pixel);}
        else{float band=a.lower-a.upper,ratio=(band+lift)/band;
            piece(c,a.clean,a.upper-2,a.lower+2,a.upper-lift-2*ratio,a.lower+2*ratio,cloth);piece(c,a.clean,0,a.upper,-lift,a.upper-lift,pixel);piece(c,a.front,a.lower,a.height,a.lower,a.height,pixel);
            c.drawBitmap(a.left,0,-lift,pixel);c.drawBitmap(a.right,0,-lift,pixel);}
        double hands=standby.holder*(standby.frame<0?1:Math.max(0,1-standby.frame/16.0));
        if(hands>0){c.save();c.translate(pose[1],pose[2]);c.scale(pose[0],pose[0]);pixel.setAlpha((int)Math.round(255*hands));c.drawBitmap(a.hands,0,0,pixel);pixel.setAlpha(255);c.restore();}
        if(standby.flash>0){double bx=a.pillow?62:342,by=a.pillow?25:19,bw=a.pillow?595:736,bh=a.pillow?1445:1317,cx=bx+bw/2,cy=by+bh/2;
            for(int i=0;i<12;i++){double t=standby.flashTime-(i%3)*12;if(t<0||t>=400)continue;double u=t%200/200,angle=(i+.5)*Math.PI/6,dx=Math.cos(angle),dy=Math.sin(angle),edge=1/Math.max(Math.abs(dx)/(bw/2),Math.abs(dy)/(bh/2)),inner=edge+30+u*90,len=100*Math.sin(Math.PI*u);int alpha=(int)Math.round(255*Math.sqrt(Math.max(0,Math.sin(Math.PI*u))));
                float x1=(float)(cx+dx*inner),y1=(float)(cy+dy*inner),x2=(float)(cx+dx*(inner+len)),y2=(float)(cy+dy*(inner+len));stroke.setColor(0xffffc928);stroke.setAlpha(alpha);stroke.setStrokeWidth(22);c.drawLine(x1,y1,x2,y2,stroke);stroke.setColor(0xfffff9c9);stroke.setAlpha(alpha);stroke.setStrokeWidth(8);c.drawLine(x1,y1,x2,y2,stroke);
            }stroke.setAlpha(255);}
    }
}
