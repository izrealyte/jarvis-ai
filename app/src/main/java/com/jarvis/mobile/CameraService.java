package com.jarvis.mobile;

import android.app.*; import android.content.*; import android.graphics.*; import android.hardware.camera2.*; import android.media.*; import java.nio.ByteBuffer; import android.os.*; import android.view.*; import java.io.*; import java.util.*;

/** User-started camera vision stream. The phone does not silently activate the camera. */
public class CameraService extends Service {
 private CameraDevice camera; private ImageReader reader; private CameraCaptureSession session; private long last;
 @Override public int onStartCommand(Intent i,int flags,int id){
  createChannel();startForeground(8,new Notification.Builder(this,"jarvis_camera").setContentTitle("JARVIS camera vision").setContentText("Camera vision is active").setSmallIcon(android.R.drawable.ic_menu_camera).build());
  try{CameraManager m=(CameraManager)getSystemService(CAMERA_SERVICE);String id0=m.getCameraIdList()[0];if(checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}m.openCamera(id0,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){camera=c;start(c);}public void onDisconnected(CameraDevice c){c.close();stopSelf();}public void onError(CameraDevice c,int e){c.close();stopSelf();}},new Handler());}catch(Exception e){stopSelf();}
  Bridge.send(this,"camera_stream","{\"active\":true}");return START_NOT_STICKY;
 }
 private void start(CameraDevice c){try{reader=ImageReader.newInstance(1280,720,ImageFormat.JPEG,2);reader.setOnImageAvailableListener(r->{long n=System.currentTimeMillis();if(n-last<1200)return;last=n;Image x=null;try{x=r.acquireLatestImage();if(x==null)return;ByteBufferOut.send(this,x); }catch(Exception ignored){}finally{if(x!=null)x.close();}},new Handler());Surface s=reader.getSurface();c.createCaptureSession(Collections.singletonList(s),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession q){session=q;try{CaptureRequest.Builder b=c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);b.addTarget(s);q.setRepeatingRequest(b.build(),null,null);}catch(Exception ignored){}}public void onConfigureFailed(CameraCaptureSession q){}},new Handler());}catch(Exception ignored){}}
 static class ByteBufferOut{static void send(Context c,Image x)throws Exception{ByteBuffer b=x.getPlanes()[0].getBuffer();byte[] d=new byte[b.remaining()];b.get(d);Bridge.sendFrame(c,d);}}
 void createChannel(){if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("jarvis_camera","JARVIS Camera Vision",NotificationManager.IMPORTANCE_LOW));}
 @Override public void onDestroy(){try{if(session!=null)session.close();if(camera!=null)camera.close();if(reader!=null)reader.close();}catch(Exception ignored){}Bridge.send(this,"camera_stream","{\"active\":false}");super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
