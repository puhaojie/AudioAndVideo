package com.phj.audioandvideo.mp4.decode;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * 描述：
 * Created by PHJ on 2019/6/5.
 */

public class ISurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    NativePlayer player;


    public void setPlayer(NativePlayer player) {
        this.player = player;
    }

    public ISurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e("TAG", "surfaceCreated: ");
        if (player != null)
            player.setSurface(getHolder().getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.e("TAG", "surfaceChanged: ");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e("TAG", "surfaceDestroyed: ");
    }
}
