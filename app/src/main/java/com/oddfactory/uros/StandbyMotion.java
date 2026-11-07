package com.oddfactory.uros;

/** Pure visual state: deliberately has no system-value setter or target value. */
final class StandbyMotion {
    String state="active";
    long started;
    double holder=1,extension=1,flash,flashTime,stretchDistance=.4;
    int frame=-1;
    boolean moving(){return state.equals("sleeping")||state.equals("waking");}
    boolean blocked(){return !state.equals("active");}
    boolean toggle(long now){if(moving())return false;state=state.equals("sleep")?"waking":"sleeping";started=now;sample(now);return true;}
    private static double smooth(double x){x=ControlMath.clamp(x);return x*x*(3-2*x);}
    void sample(long now){
        long t=Math.max(0,now-started);
        if(moving()&&t>=900)state=state.equals("sleeping")?"sleep":"active";
        holder=extension=1;frame=-1;flash=flashTime=0;
        if(state.equals("sleep")){holder=extension=0;frame=48;}
        if(moving()){
            boolean wake=state.equals("waking");flashTime=t;flash=t<420?1:0;
            holder=wake?smooth(t/100.0):1-smooth(t/100.0);
            double progress=smooth((t-100)/800.0),path=(wake?progress:1-progress)*(1+stretchDistance);
            extension=stretchDistance>0?(path>=1+stretchDistance?1:ControlMath.clamp((path-1)/stretchDistance)):0;
            frame=path>=1?-1:Math.min(48,(int)Math.ceil(48*(1-path)));
        }
    }
}
