package com.phj.audioandvideo.camera.surfce;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.phj.audioandvideo.R;


public class SurfaceCameraActivity extends AppCompatActivity {

    SurfaceCameraPreview1 surfaceView;
    CameraManager mCameraManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //去掉标题栏
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        //全屏，隐藏状态
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        setContentView(R.layout.activity_surface_camera);

        surfaceView = (SurfaceCameraPreview1) findViewById(R.id.surfaceView);
        mCameraManager = new CameraManager();
        mCameraManager.setSurfaceView(surfaceView);
        mCameraManager.setWidth(128*3);
        mCameraManager.setHeight(192*3);

        mCameraManager.openBackFacingCameraGingerbread();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mCameraManager.openBackFacingCameraGingerbread();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (event.getAction() == MotionEvent.ACTION_MOVE && surfaceView != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) surfaceView.getLayoutParams();
            layoutParams.setMargins((int)event.getX(),(int)event.getY(),0,0);
            Log.e("TAG", "onTouchEvent: "+event.getX());
            surfaceView.setLayoutParams(layoutParams);
        }
        return super.onTouchEvent(event);
    }


    @Override
    protected void onPause() {
        super.onPause();
        mCameraManager.release();
    }
}
