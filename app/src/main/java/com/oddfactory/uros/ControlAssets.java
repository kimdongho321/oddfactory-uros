package com.oddfactory.uros;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

final class ControlAssets {
    final boolean pillow;
    final int width, height, upper, lower, head, baseline, maxLift;
    final Bitmap front, rear, left, right;
    final Bitmap clean, hands;
    final Bitmap[] standbyFrames;
    final float[][] standbyPoses;
    final float[] rest;
    ControlAssets(Context context, boolean isPillow) throws Exception {
        pillow=isPillow;
        String root="data/";
        JSONObject manifest;
        try(InputStream input=context.getAssets().open(root+(pillow?"b.json":"a.json"))) {
            java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
            byte[] chunk=new byte[4096]; int count;
            while((count=input.read(chunk))!=-1) buffer.write(chunk,0,count);
            manifest=new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        }
        if(manifest.getInt("version")!=3) throw new IllegalArgumentException("rig version");
        JSONObject rig=manifest.getJSONObject("rig"), files=rig.getJSONObject("files");
        width=rig.getInt("width"); height=rig.getInt("height"); upper=rig.getInt("upper"); lower=rig.getInt("lower");
        head=rig.getInt("headTop"); baseline=rig.getInt("baselineCharacterHeight"); maxLift=rig.optInt("maxLift",705);
        if(width<1 || height<1 || head>=upper || upper>=lower || lower>=height || baseline<=0 || maxLift<0) throw new IllegalArgumentException("rig bounds");
        front=load(context,root+files.getString("front"),width,height);
        rear=load(context,root+files.getString("rear"),width,height);
        left=load(context,root+files.getString("sleeveLeft"),width,height);
        right=load(context,root+files.getString("sleeveRight"),width,height);
        JSONObject sleep=manifest.getJSONObject("standby");
        clean=load(context,root+sleep.getString("clean"),width,height);hands=load(context,root+sleep.getString("hands"),width,height);
        JSONArray paths=sleep.getJSONArray("files"),poses=sleep.getJSONArray("poses"),bounds=sleep.getJSONArray("rest");
        if(paths.length()!=49||poses.length()!=49)throw new IllegalArgumentException("standby frames");
        rest=new float[4];for(int i=0;i<4;i++)rest[i]=(float)bounds.getDouble(i);
        standbyFrames=new Bitmap[49];standbyPoses=new float[49][3];
        for(int i=0;i<49;i++){standbyFrames[i]=load(context,root+paths.getString(i),sleep.getInt("width"),sleep.getInt("height"));for(int j=0;j<3;j++)standbyPoses[i][j]=(float)poses.getJSONArray(i).getDouble(j);}
    }
    private static Bitmap load(Context c,String path,int w,int h) throws Exception {
        if(path.contains("..")) throw new IllegalArgumentException("asset path");
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inScaled=false;
        try(InputStream input=c.getAssets().open(path)) {
            Bitmap b=BitmapFactory.decodeStream(input,null,options);
            if(b==null || b.getWidth()!=w || b.getHeight()!=h) throw new IllegalArgumentException("asset dimensions "+path);
            return b;
        }
    }
}
