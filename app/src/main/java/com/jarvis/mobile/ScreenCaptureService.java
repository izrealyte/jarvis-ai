package com.jarvis.mobile;

import android.app.*; import android.content.*; import android.graphics.*; import android.hardware.display.*; import android.media.*; import android.media.projection.*; import android.os.*; import android.util.DisplayMetrics; import java.io.*;

/** User-consented screen stream. Frames are sent as JPEG to the JARVIS server for OCR/vision analysis. */
public class ScreenCaptureService extends Service {
 private MediaProjection p; private ImageReader r; private VirtualDisplay d; private long lastFrame;
 @Override public int onStartCommand(Intent i,int flags,int id){createChannel();startForeground(7,notification());try{
  MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);p=m.getMediaProjection(i.getIntExtra("resultCode",0),(Intent)i.getParcelableExtra("data"));
  p.registerCallback(new MediaProjection.Callback(){public void onStop(){stopSelf();}},new Handler());
  DisplayMetrics dm=getResources().getDisplayMetrics();int w=Math.min(dm.widthPixels,1440),h=(int)(dm.heightPixels*(w/(float)dm.widthPixels));
  r=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);r.setOnImageAvailableListener(x->frame(x),new Handler(Looper.getMainLooper()));
  d=p.createVirtualDisplay("JARVIS",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,r.getSurface(),null,null);
  Bridge.send(this,"screen_stream","{\"active\":true,\"width\":"+w+",\"height\":"+h+"}");
 }catch(Exception e){stopSelf();}return START_NOT_STICKY;}
 private void frame(ImageReader x){long now=System.currentTimeMillis();if(now-lastFrame<900)return;lastFrame=now;Image im=null;try{im=x.acquireLatestImage();if(im==null)return;Image.Plane pl=im.getPlanes()[0];int w=im.getWidth(),h=im.getHeight(),ps=pl.getPixelStride(),rs=pl.getRowStride(),pad=rs-ps*w;Bitmap b=Bitmap.createBitmap(w+pad/ps,h,Bitmap.Config.ARGB_8888);b.copyPixelsFromBuffer(pl.getBuffer());Bitmap crop=Bitmap.createBitmap(b,0,0,w,h);ByteArrayOutputStream o=new ByteArrayOutputStream();crop.compress(Bitmap.CompressFormat.JPEG,55,o);Bridge.sendFrame(this,o.toByteArray());crop.recycle();b.recycle();}catch(Exception ignored){}finally{if(im!=null)im.close();}}
 Notification notification(){return new Notification.Builder(this,"jarvis_capture").setContentTitle("JARVIS screen vision").setContentText("Screen capture is active").setSmallIcon(android.R.drawable.ic_menu_view).build();}
 void createChannel(){if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("jarvis_capture","JARVIS Screen Vision",NotificationManager.IMPORTANCE_LOW));}
 @Override public void onDestroy(){if(d!=null)d.release();if(r!=null)r.close();if(p!=null)p.stop();Bridge.send(this,"screen_stream","{\"active\":false}");super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
