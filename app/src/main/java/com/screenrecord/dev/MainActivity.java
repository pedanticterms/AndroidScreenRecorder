package com.screenrecord.dev;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.screenrecord.dev.R;
import com.screenrecord.dev.ScreenRecordService;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_SCREEN_RECORD_PERMISSION = 1;
    private boolean isRecording = false;
    private MediaProjectionManager mediaProjectionManager;
    private int resultCode;
    private Intent data;

    private final static String TAG = "ScreenRecord";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        Button startButton = findViewById(R.id.btn_start_recorder);
        Button stopButton = findViewById(R.id.btn_stop_recorder);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    checkScreenRecordPermission();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });
    }

    private void checkScreenRecordPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_SCREEN_RECORD_PERMISSION);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        Log.d(TAG, "MainActivity: startRecording");
        isRecording = true;
        // 请求屏幕录制权限
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_SCREEN_RECORD_PERMISSION);
    }

    private void stopRecording() {
        isRecording = false;
        Intent intent = new Intent(this, ScreenRecordService.class);
        stopService(intent);
//        Intent intent = new Intent("com.screenrecord.dev");
//        sendBroadcast(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SCREEN_RECORD_PERMISSION) {
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_RECORD_PERMISSION) {
            if (resultCode == RESULT_OK) {
                // 在这里启动 ScreenRecordService 并传递 resultCode 和 data
                this.resultCode = resultCode;
                this.data = data;
                Intent intent = new Intent(MainActivity.this, ScreenRecordService.class);
                intent.putExtra("resultCode", resultCode);
                intent.putExtra("data", data);
                startService(intent);
            }
        }
    }
}
