package com.oddfactory.uros;

/** A hold owns placement; movement before the hold owns the system value. */
final class ControlGesture {
    enum Mode { IDLE, PRESSED, VALUE, POSITION }
    Mode mode=Mode.IDLE;
    private float x,y,slop;
    private long at,timeout;
    void begin(float px,float py,long now,float touchSlop,long holdTimeout) {
        x=px;y=py;at=now;slop=touchSlop;timeout=holdTimeout;mode=Mode.PRESSED;
    }
    void hold(long now) { if(mode==Mode.PRESSED && now-at>=timeout)mode=Mode.POSITION; }
    Mode move(float px,float py,long now) {
        hold(now);
        if(mode==Mode.PRESSED && Math.hypot(px-x,py-y)>slop) mode=Mode.VALUE;
        return mode;
    }
    void cancel() { mode=Mode.IDLE; }
}
