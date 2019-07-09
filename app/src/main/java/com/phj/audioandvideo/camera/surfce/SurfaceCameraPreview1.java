package com.phj.audioandvideo.camera.surfce;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * 描述：采用camera + SurfaceView 预览相机数据（nv21格式，YUV420的格式一种）
 * Created by PHJ on 2019/6/4.
 */

public class SurfaceCameraPreview1 extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = SurfaceCameraPreview1.class.getSimpleName();

    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private CameraPreviewListener mListener;

    public SurfaceCameraPreview1(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
    }


    public void setCamera(Camera camera) {
        this.mCamera = camera;
    }

    public void setPreviewListener(CameraPreviewListener listener) {
        this.mListener = listener;
    }

    /**
     * 相机预览
     * @param holder SurfaceHolder
     */
    private void restartPreview(SurfaceHolder holder) {
        if (mCamera != null && holder.getSurface() != null) {

            mCamera.stopPreview();

            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
                // 开始预览的通知
                if (mListener != null) {
                    mListener.onStartPreview();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "surfaceCreated: " );
        restartPreview(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.e(TAG, "surfaceCreated: " );
        restartPreview(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

}
