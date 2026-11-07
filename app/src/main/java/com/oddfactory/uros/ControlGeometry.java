package com.oddfactory.uros;

/** The visible surface never moves or resizes for a value/standby change.
 * Only a separate transparent input window follows the smaller touch target. */
final class ControlGeometry {
    int x,y,width,height,touchX,touchY,touchWidth,touchHeight;
    float originX,originY;
    static ControlGeometry create(boolean pillow,int sw,int sh,int head,float scale,float density,float maxLift,int anchor,int left,int top,int right,int bottom,double value,boolean sleep,float[] rest){
        ControlGeometry g=new ControlGeometry();
        float margin=5*density,outer=Math.min(margin,220*scale),inner=220*scale;
        float lp=pillow?outer:inner,rp=pillow?inner:outer;
        float crop=Math.min(0,head-maxLift-8),baseWidth=sw*scale,baseHeight=(sh-crop)*scale+24*density;
        float tp=Math.min(220*scale,Math.max(0,anchor-baseHeight-top)),bp=Math.min(220*scale,Math.max(0,bottom-anchor));
        float baseX=pillow?left+margin:right-baseWidth-margin;
        g.x=Math.round(baseX-lp);g.y=Math.round(anchor-baseHeight-tp);
        g.width=Math.round(baseWidth+lp+rp);g.height=Math.round(baseHeight+tp+bp);
        // Derive from the rounded physical window origin to keep the floor exact.
        g.originX=baseX-g.x;g.originY=anchor-g.y-sh*scale;
        float inputCrop=Math.min(0,head-(float)value*maxLift-8);
        g.touchWidth=Math.round(baseWidth);g.touchHeight=Math.round((sh-inputCrop)*scale+24*density);
        if(sleep){g.touchWidth=Math.max(Math.round(48*density),Math.round(rest[2]*scale));g.touchHeight=Math.max(Math.round(48*density),Math.round((sh-rest[1])*scale));}
        g.touchX=pillow?Math.round(left+margin):Math.round(right-margin)-g.touchWidth;
        g.touchY=anchor-g.touchHeight;
        return g;
    }
}
