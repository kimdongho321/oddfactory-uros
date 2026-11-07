package com.oddfactory.uros;
/** A candidate down pauses the lower-count action until up, hold, or cancel. */
final class TapSequence {
    private int count;
    private long at;
    private float x,y;
    void down(float px,float py,long now,long timeout,float slop) {
        if(count==0||now-at<0||now-at>timeout||Math.hypot(px-x,py-y)>slop)clear();
    }
    int up(float px,float py,long now) { x=px;y=py;at=now;return ++count; }
    int take() {int result=count;clear();return result;}
    void clear(){count=0;at=0;}
}
