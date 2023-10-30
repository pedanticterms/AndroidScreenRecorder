package com.screenrecord.dev;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class ScreenRecordService extends Service {

    //录屏工具MediaProjection
    private MediaProjection mediaProjection;
    //录像机MediaRecorder
    private MediaRecorder mediaRecorder;
    //用于录屏的虚拟屏幕
    private VirtualDisplay virtualDisplay;
    //声明录制屏幕的宽高像素
    private int width;
    private int height;
    //    private int width = 720;
//    private int height = 1080;
    private int dpi;
    //标志，判断是否正在录屏
    private boolean running;
    //声明视频存储路径
    private String videoPath = "";
    private static final int NOTIFICATION_ID = 123;
    private final static String TAG = "ScreenRecord";
    private boolean isRecording = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service: onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service: onStartCommand");
        // 创建前台通知，确保服务不会被终止
        buildNotification();
        // 获取MediaProjectionManager
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // 获取MediaProjection，需要在之前获得录制权限的情况下才能成功
        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");
        Log.d(TAG, "Service: resultCode is " + resultCode + ",data is " + data);
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);

        getScreenSize();
        initRecorder();
        createVirtualDisplay();
        isRecording = true;

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void stopRecording() {
        try {
            // 停止屏幕录制
            if (isRecording) {
                Log.i(TAG, "Service onDestroy");
                if (virtualDisplay != null) {
                    virtualDisplay.release();
                    virtualDisplay = null;
                }
                if (mediaRecorder != null) {
                    mediaRecorder.setOnErrorListener(null);
                    mediaRecorder.stop();
                    mediaRecorder.reset();
                    mediaRecorder.release();
                    mediaRecorder = null;
                }
                if (mediaProjection != null) {
                    mediaProjection.stop();
                    mediaProjection = null;
                }

//                mediaRecorder.setOnErrorListener(null);
//                mediaRecorder.setOnInfoListener(null);
//                mediaRecorder.setPreviewDisplay(null);
                isRecording = false;
            }
            stopSelf();
        } catch (Exception e) {
            Log.i(TAG, "exception => " + e.toString());
        }
    }
    private void buildNotification() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 注册通知渠道
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // 创建通知渠道
            String channelId = "screen_record_id"; // 通道ID，可以自定义
            CharSequence channelName = "screen_record_name"; // 通道名称，可以自定义
            String channelDescription = "screen_record_working"; // 通道描述，可以自定义
            int importance = NotificationManager.IMPORTANCE_HIGH; // 通知的重要性级别

            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, importance);
            notificationChannel.setDescription(channelDescription);
            notificationManager.createNotificationChannel(notificationChannel);

            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);

            Notification notification = builder.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("屏幕录制进行中")
                .setContentText("点击以停止录制")
                .setContentIntent(pendingIntent)
                .build();
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    //初始化Recorder录像机
    public void initRecorder() {
        Log.d(TAG, "initRecorder");
        //新建Recorder
        mediaRecorder = new MediaRecorder();
        //设置录像机的一系列参数
        //设置音频来源
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        //设置视频来源
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        //设置视频格式为mp4
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        //设置视频码率
        mediaRecorder.setVideoEncodingBitRate(2 * 1920 * 1080);
        //设置音频编码
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        //设置视频编码为H.264
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //设置视频大小，清晰度
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoFrameRate(18);
        //设置视频存储地址，返回的文件夹下的命名为当前系统事件的文件
        videoPath = "screenrecord" + System.currentTimeMillis() + ".mp4";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createScreenRecordFile(videoPath);
        }
        //初始化完成，进入准备阶段，准备被使用
        //截获异常，处理
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            //异常提示
            Toast.makeText(this,
                    "Recorder录像机prepare失败，无法使用，请重新初始化！",
                    Toast.LENGTH_SHORT).show();
        }
        try {
            virtualDisplay = mediaProjection.createVirtualDisplay("VirtualScreen", width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(this,"virtualDisplay创建录屏异常，请退出重试！",Toast.LENGTH_SHORT).show();
        }
        mediaRecorder.start();
    }

    private void getScreenSize() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        display.getMetrics(displayMetrics);

        width = displayMetrics.widthPixels;
        height = displayMetrics.heightPixels;
        dpi = displayMetrics.densityDpi;
    }

    public void createVirtualDisplay() {
        //虚拟屏幕通过MediaProjection获取，传入一系列传过来的参数
        //可能创建时会出错，捕获异常
        Log.d(TAG, "createVirtualDisplay");
        try {
            virtualDisplay = mediaProjection.createVirtualDisplay("VirtualScreen", width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(this,"virtualDisplay创建录屏异常，请退出重试！",Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void createScreenRecordFile(String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);

        ContentResolver contentResolver = getContentResolver();
        Uri uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        try {
            ParcelFileDescriptor parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "w");
            mediaRecorder.setOutputFile(parcelFileDescriptor.getFileDescriptor());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
