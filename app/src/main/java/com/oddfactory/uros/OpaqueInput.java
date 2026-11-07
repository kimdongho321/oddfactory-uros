package com.oddfactory.uros;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.List;

/** Only fully opaque cells receive touches. The display canvas is never touchable. */
final class OpaqueInput {
    interface Touch { boolean accept(MotionEvent event); }
    private final Context context;
    private final WindowManager windows;
    private final WindowManager.LayoutParams template;
    private final Touch touch;
    private static final class Entry {
        final View view;
        final WindowManager.LayoutParams params;
        final Rect bounds;
        Entry(View v,WindowManager.LayoutParams p,Rect r){view=v;params=p;bounds=new Rect(r);}
    }
    private final List<Entry> entries=new ArrayList<>();
    private View owner;
    private long rebuilds,added,updated,removed,downs,ups,cancels,deferred;
    OpaqueInput(Context c,WindowManager w,WindowManager.LayoutParams p,Touch t){context=c;windows=w;template=p;touch=t;}
    int count(){return entries.size();}
    String diagnostics(){return "rebuilds="+rebuilds+" add="+added+" update="+updated+" remove="+removed+" down="+downs+" up="+ups+" cancel="+cancels+" deferred="+deferred+" owned="+(owner!=null);}
    void releaseOwner(){owner=null;}
    void clear(){
        if(owner!=null){
            MotionEvent event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,0,0,0);
            try{owner.dispatchTouchEvent(event);}finally{event.recycle();owner=null;}
        }
        for(Entry entry:entries){windows.removeViewImmediate(entry.view);removed++;}
        entries.clear();
    }
    boolean rebuild(List<int[]> cells,int x,int y,Rect safe){
        // A captured gesture keeps its actual input window through UP/CANCEL.
        if(owner!=null){deferred++;return false;}
        rebuilds++;
        List<Rect> bounds=new ArrayList<>();
        for(int[] cell:cells){
            Rect r=new Rect(x+cell[0],y+cell[1],x+cell[2],y+cell[3]);
            if(r.intersect(safe))bounds.add(r);
        }
        List<Entry> previous=new ArrayList<>(entries);
        boolean[] used=new boolean[previous.size()];
        Entry[] next=new Entry[bounds.size()];
        // Reserve identical windows first, including when the cell order changes.
        for(int i=0;i<bounds.size();i++)for(int j=0;j<previous.size();j++){
            if(!used[j]&&previous.get(j).bounds.equals(bounds.get(i))){next[i]=previous.get(j);used[j]=true;break;}
        }
        try {
            for(int i=0;i<next.length;i++){
                if(next[i]!=null)continue;
                Rect r=bounds.get(i);
                for(int j=0;j<previous.size();j++)if(!used[j]){
                    Entry entry=previous.get(j);used[j]=true;next[i]=entry;
                    entry.bounds.set(r);entry.params.x=r.left;entry.params.y=r.top;
                    entry.params.width=r.width();entry.params.height=r.height();
                    windows.updateViewLayout(entry.view,entry.params);updated++;break;
                }
                if(next[i]!=null)continue;
                View v=new View(context){
                    @SuppressLint("ClickableViewAccessibility") @Override public boolean onTouchEvent(MotionEvent event){
                        int action=event.getActionMasked();
                        if(action==MotionEvent.ACTION_DOWN&&owner==null){owner=this;downs++;}
                        if(owner!=this)return true;
                        boolean accepted=touch.accept(event);
                        if(action==MotionEvent.ACTION_UP){ups++;owner=null;}
                        else if(action==MotionEvent.ACTION_CANCEL){cancels++;owner=null;}
                        return accepted;
                    }
                    @Override public boolean performClick(){super.performClick();return true;}
                };
                WindowManager.LayoutParams p=new WindowManager.LayoutParams();p.copyFrom(template);
                p.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;p.x=r.left;p.y=r.top;p.width=r.width();p.height=r.height();
                p.setTitle("Uros character input");
                windows.addView(v,p);added++;
                next[i]=new Entry(v,p,r);entries.add(next[i]);
            }
            for(int j=0;j<previous.size();j++)if(!used[j]){windows.removeViewImmediate(previous.get(j).view);removed++;}
            entries.clear();for(Entry entry:next)entries.add(entry);
            return true;
        }catch(RuntimeException error){clear();throw error;}
    }
}
