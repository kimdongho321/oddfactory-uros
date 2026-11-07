package com.oddfactory.uros;

import java.util.ArrayList;
import java.util.List;

/** Conservative public-API hit windows: every physical pixel in every cell is opaque.
 * Identical horizontal runs merge vertically. No hidden Android touch-region API. */
final class OpaqueCells {
    static List<int[]> rectangles(int[] pixels,int width,int height,int cell) {
        if(width<=0||height<=0||cell<=0||pixels.length!=width*height)throw new IllegalArgumentException("mask size");
        List<int[]> result=new ArrayList<>();
        List<int[]> previous=new ArrayList<>();
        for(int y=0;y<height;y+=cell) {
            List<int[]> row=new ArrayList<>();
            int run=-1;
            for(int x=0;x<=width;x+=cell) {
                int end=Math.min(width,x+cell),bottom=Math.min(height,y+cell);
                boolean opaque=x<width;
                for(int py=y;opaque&&py<bottom;py++)for(int px=x;px<end;px++)if((pixels[py*width+px]>>>24)<=24){opaque=false;break;}
                if(opaque&&run<0)run=x;
                if(!opaque&&run>=0) {
                    int[] rectangle=null;
                    for(int[] p:previous)if(p[0]==run&&p[2]==x&&p[3]==y){rectangle=p;break;}
                    if(rectangle==null){rectangle=new int[]{run,y,x,bottom};result.add(rectangle);}else rectangle[3]=bottom;
                    row.add(rectangle);run=-1;
                }
                // Last partial cell must also close the run.
                if(x<width&&end==width&&run>=0) {
                    int[] rectangle=null;
                    for(int[] p:previous)if(p[0]==run&&p[2]==width&&p[3]==y){rectangle=p;break;}
                    if(rectangle==null){rectangle=new int[]{run,y,width,bottom};result.add(rectangle);}else rectangle[3]=bottom;
                    row.add(rectangle);run=-1;
                }
            }
            previous=row;
        }
        return result;
    }
}
