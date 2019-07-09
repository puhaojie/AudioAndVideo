package com.phj.audioandvideo.mp4.decode;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.phj.audioandvideo.R;


/**
 *  播放mp4的Activity
 */
public class PlayMp4Activity extends AppCompatActivity implements View.OnClickListener {

    private boolean isPause;
    private NativePlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //去掉标题栏
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        //全屏，隐藏状态
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        setContentView(R.layout.activity_play);


        ISurfaceView surfaceView = (ISurfaceView) findViewById(R.id.ISurfaceView);
        Surface surface = surfaceView.getHolder().getSurface();
//        SimplePlayer simplePlayer = new SimplePlayer(surface, Environment.getExternalStorageDirectory()+"/5.mp4");
//        simplePlayer.play();

        player = new NativePlayer(surface);
        surfaceView.setPlayer(player);
        player.setPath(Environment.getExternalStorageDirectory()+"/5.mp4");
        player.start();

        surfaceView.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Log.e("TAG", "onClick: "+isPause );
        if (player.isStop())
            return;
        if (isPause) {
            player.continuePlay();
        } else {
            player.pause();
        }

        isPause = !isPause;
    }

    /**
     *  todo 当切换到前台的时候这个，SurfaceView会被销毁，即Surface会被释放
     */
    @Override
    protected void onPause() {
        super.onPause();
        player.pause();
        isPause = true;
    }

}
