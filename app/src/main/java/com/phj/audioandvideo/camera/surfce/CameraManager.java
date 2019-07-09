package com.phj.audioandvideo.camera.surfce;


import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceView;

/**
 * 描述：相机预览的管理
 * Created by PHJ on 2019/6/4.
 */

public class CameraManager implements Camera.PreviewCallback, CameraPreviewListener {

    private final static String TAG = CameraManager.class.getSimpleName();

    //相机
    private Camera camera;
    private byte[] cameraPreviewBuffer;//1.5 for yuv image


    // 预览的宽度和高度
    private int width;
    private int height;

    private SurfaceCameraPreview1 mSurfaceView;

    // 预览旋转角度
    private int cameraOrientation = 90;

    public void setSurfaceView(SurfaceView mSurfaceView) {
        this.mSurfaceView = (SurfaceCameraPreview1) mSurfaceView;
        this.mSurfaceView.setPreviewListener(this);
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void release() {
        Log.e("onPreviewFrame", "release: " );
        if (camera != null) {
            this.mSurfaceView.setCamera(null);
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    /**
     * 打开相机预览
     *
     * @return Camera
     */
    public Camera openBackFacingCameraGingerbread() {

            if (camera != null) {
                camera.setPreviewCallback(null);
                camera.release();
            }

            try {
                camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
                mSurfaceView.setCamera(camera);

                camera.setPreviewCallback(this);
                camera.setDisplayOrientation(cameraOrientation);
                final Camera.Parameters parameters = camera.getParameters();
                final Camera.Size size = getBestPreviewSize(width, height);

                parameters.setPreviewSize(size.width, size.height);

                camera.setParameters(parameters);
                Log.e(TAG, "openBackFacingCameraGingerbread: " );
            } catch (Exception e) {
               e.printStackTrace();
            }


        return camera;
    }

    /**
     * 获取相机最合适的预览尺寸
     *
     * @param width width
     * @param height height
     * @return Camera.Size
     */
    private Camera.Size getBestPreviewSize(int width, int height) {
        Camera.Size result = null;
        final Camera.Parameters p = camera.getParameters();
        //特别注意此处需要规定rate的比是大的比小的，不然有可能出现rate = height/width，但是后面遍历的时候，current_rate = width/height,所以我们限定都为大的比小的。
        float rate = (float) Math.max(width, height) / (float) Math.min(width, height);
        float tmp_diff;
        float min_diff = -1f;
        for (Camera.Size size : p.getSupportedPreviewSizes()) {
            float current_rate = (float) Math.max(size.width, size.height) / (float) Math.min(size.width, size.height);
            tmp_diff = Math.abs(current_rate - rate);
            if (min_diff < 0) {
                min_diff = tmp_diff;
                result = size;
            }
            if (tmp_diff < min_diff) {
                min_diff = tmp_diff;
                result = size;
            }
        }
        return result;
    }

    /**
     * 获取相机预览宽
     *
     * @return int
     */
    private int getPreviewWidth() {
        Camera.Parameters parameters = camera.getParameters();
        return parameters.getPreviewSize().width;

    }

    /**
     * 获取相机预览高
     *
     * @return int
     */
    private int getPreviewHeight() {
        Camera.Parameters parameters = camera.getParameters();
        return parameters.getPreviewSize().height;
    }

    int i =0;
    // 数据到达
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        camera.addCallbackBuffer(cameraPreviewBuffer);
//        Log.e(TAG, "onPreviewFrame: data="+data.length );
    }


    @Override
    public void onStartPreview() {
        Log.e(TAG, "onStartPreview: " );

        cameraPreviewBuffer = new byte[(int) (getPreviewWidth() * getPreviewHeight() * 1.5)];//1.5 for yuv image
        camera.addCallbackBuffer(cameraPreviewBuffer);
        camera.setPreviewCallbackWithBuffer(this);
    }
}
