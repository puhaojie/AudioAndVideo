package com.phj.audioandvideo.camera.texture;


import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;


import com.phj.audioandvideo.base.ICamera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class CameraProxy implements ICamera,Camera.AutoFocusCallback {

    private static final String TAG = "CameraProxy";

    private Activity mActivity;
    private Camera mCamera;
    private Parameters mParameters;
    private CameraInfo mCameraInfo = new CameraInfo();
    private int mPreviewWidth = 1440; // default 1440
    private int mPreviewHeight = 1080; // default 1080
    private float mPreviewScale = mPreviewHeight * 1f / mPreviewWidth;
    private OrientationEventListener mOrientationEventListener;


    public byte[] mPreviewBuffer;
    private Point mPicSize = new Point();
    private Point mPreSize = new Point();

    public CameraProxy(Activity activity) {
        mActivity = activity;
        mOrientationEventListener = new OrientationEventListener(mActivity) {
            @Override
            public void onOrientationChanged(int orientation) {
                Log.d(TAG, "onOrientationChanged: orientation: " + orientation);
                setPictureRotate(orientation);
            }
        };
    }


    private void initConfig() {
        Log.v(TAG, "initConfig");
        try {
            mParameters = mCamera.getParameters();
            // 如果摄像头不支持这些参数都会出错的，所以设置的时候一定要判断是否支持
            List<String> supportedFlashModes = mParameters.getSupportedFlashModes();
            if (supportedFlashModes != null && supportedFlashModes.contains(Parameters.FLASH_MODE_OFF)) {
                mParameters.setFlashMode(Parameters.FLASH_MODE_OFF); // 设置闪光模式
            }
            List<String> supportedFocusModes = mParameters.getSupportedFocusModes();
            if (supportedFocusModes != null && supportedFocusModes.contains(Parameters.FOCUS_MODE_AUTO)) {
                mParameters.setFocusMode(Parameters.FOCUS_MODE_AUTO); // 设置聚焦模式
            }
            mParameters.setPreviewFormat(ImageFormat.NV21); // 设置预览图片格式
            mParameters.setPictureFormat(ImageFormat.JPEG); // 设置拍照图片格式
            mParameters.setExposureCompensation(0); // 设置曝光强度
            Size previewSize = getSuitableSize(mParameters.getSupportedPreviewSizes());
            mPreviewWidth = previewSize.width;
            mPreviewHeight = previewSize.height;
            mParameters.setPreviewSize(mPreviewWidth, mPreviewHeight); // 设置预览图片大小
            Log.d(TAG, "previewWidth: " + mPreviewWidth + ", previewHeight: " + mPreviewHeight);
            Size pictureSize = getSuitableSize(mParameters.getSupportedPictureSizes());
            mParameters.setPictureSize(pictureSize.width, pictureSize.height);
            mPicSize = new Point(pictureSize.width,pictureSize.height);
            mPreSize = new Point(mPreviewWidth,mPreviewHeight);
            Log.d(TAG, "pictureWidth: " + pictureSize.width + ", pictureHeight: " + pictureSize.height);
            mCamera.setParameters(mParameters); // 将设置好的parameters添加到相机里
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Size getSuitableSize(List<Size> sizes) {
        int minDelta = Integer.MAX_VALUE; // 最小的差值，初始值应该设置大点保证之后的计算中会被重置
        int index = 0; // 最小的差值对应的索引坐标
        for (int i = 0; i < sizes.size(); i++) {
            Size previewSize = sizes.get(i);
            Log.v(TAG, "SupportedPreviewSize, width: " + previewSize.width + ", height: " + previewSize.height);
            // 找到一个与设置的分辨率差值最小的相机支持的分辨率大小
            if (previewSize.width * mPreviewScale == previewSize.height) {
                int delta = Math.abs(mPreviewWidth - previewSize.width);
                if (delta == 0) {
                    return previewSize;
                }
                if (minDelta > delta) {
                    minDelta = delta;
                    index = i;
                }
            }
        }
        return sizes.get(index); // 默认返回与设置的分辨率最接近的预览尺寸
    }

    /**
     * 设置相机显示的方向，必须设置，否则显示的图像方向会错误
     */
    private void setDisplayOrientation() {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (mCameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
            result = (mCameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (mCameraInfo.orientation - degrees + 360) % 360;
        }
        mCamera.setDisplayOrientation(result);
    }

    private void setPictureRotate(int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        orientation = (orientation + 45) / 90 * 90;
        int rotation = 0;
        if (mCameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (mCameraInfo.orientation - orientation + 360) % 360;
        } else {  // back-facing camera
            rotation = (mCameraInfo.orientation + orientation) % 360;
        }
        Log.d(TAG, "picture rotation: " + rotation);
    }


    private void addBuffer() {
        if (mPreviewBuffer == null) {
            mPreviewBuffer = new byte[mPreviewWidth * mPreviewHeight * 3 / 2];
        }
        mCamera.addCallbackBuffer(mPreviewBuffer);
    }


    public void focusOnPoint(int x, int y, int width, int height) {
        Log.v(TAG, "touch point (" + x + ", " + y + ")");
        if (mCamera == null) {
            return;
        }
        Parameters parameters = mCamera.getParameters();
        // 1.先要判断是否支持设置聚焦区域
        if (parameters.getMaxNumFocusAreas() > 0) {
            // 2.以触摸点为中心点，view窄边的1/4为聚焦区域的默认边长
            int length = Math.min(width, height) >> 3; // 1/8的长度
            int left = x - length;
            int top = y - length;
            int right = x + length;
            int bottom = y + length;
            // 3.映射，因为相机聚焦的区域是一个(-1000,-1000)到(1000,1000)的坐标区域
            left = left * 2000 / width - 1000;
            top = top * 2000 / height - 1000;
            right = right * 2000 / width - 1000;
            bottom = bottom * 2000 / height - 1000;
            // 4.判断上述矩形区域是否超过边界，若超过则设置为临界值
            left = left < -1000 ? -1000 : left;
            top = top < -1000 ? -1000 : top;
            right = right > 1000 ? 1000 : right;
            bottom = bottom > 1000 ? 1000 : bottom;
            Log.d(TAG, "focus area (" + left + ", " + top + ", " + right + ", " + bottom + ")");
            ArrayList<Camera.Area> areas = new ArrayList<>();
            areas.add(new Camera.Area(new Rect(left, top, right, bottom), 600));
            parameters.setFocusAreas(areas);
        }
        try {
            mCamera.cancelAutoFocus(); // 先要取消掉进程中所有的聚焦功能
            mCamera.setParameters(parameters);
            mCamera.autoFocus(this); // 调用聚焦
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 方法功能
     * @param isZoomIn 放大倍数
     */
    public void handleZoom(boolean isZoomIn) {
        if (mParameters.isZoomSupported()) {
            int maxZoom = mParameters.getMaxZoom();
            int zoom = mParameters.getZoom();
            if (isZoomIn && zoom < maxZoom) {
                zoom++;
            } else if (zoom > 0) {
                zoom--;
            }
            Log.d(TAG, "handleZoom: zoom: " + zoom);
            mParameters.setZoom(zoom);
            mCamera.setParameters(mParameters);
        } else {
            Log.i(TAG, "zoom not supported");
        }
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        Log.d(TAG, "onAutoFocus: " + success);
    }

    @Override
    public boolean open(int cameraId) {
        close();
        Log.d(TAG, "openCamera cameraId: " + cameraId);
        mCamera = Camera.open(cameraId);
        Camera.getCameraInfo(cameraId, mCameraInfo);
        initConfig();
        setDisplayOrientation();
        Log.d(TAG, "openCamera enable mOrientationEventListener");
        mOrientationEventListener.enable();
        return true;
    }

    @Override
    public boolean changeTo(int cameraId) {
        close();
        open(cameraId);
        return false;
    }

    @Override
    public void setPreviewTexture(SurfaceTexture texture) {
        if (mCamera != null) {
            Log.v(TAG, "startPreview");
            try {
                mCamera.setPreviewTexture(texture);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setConfig(Config config) {

    }

    @Override
    public void startPreview() {
        addBuffer();
        if (mCamera != null) {
            mCamera.startPreview();
        }
    }

    @Override
    public void takePhoto(final TakePhotoCallback callback) {
        if (callback == null)
            return;
        mCamera.takePicture(null, new Camera.PictureCallback(){

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                if (data == null)
                    return;
                callback.onTakePhoto(data,mPicSize.x,mPicSize.y);
            }
        },  new Camera.PictureCallback(){

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                if (data == null)
                    return;
                callback.onTakePhoto(data,mPicSize.x,mPicSize.y);
            }
        });
    }

    @Override
    public void setOnPreviewFrameCallback(final PreviewFrameCallback callback) {
        if (callback == null)
            return;
        if (mCamera != null) {
            mCamera.setPreviewCallbackWithBuffer(new PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    addBuffer();
                    callback.onPreviewFrame(data,mPreSize.x,mPreSize.y);

                }
            });
        }
    }

    @Override
    public void close() {
        if (mCamera != null) {
            Log.v(TAG, "releaseCamera");
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        mOrientationEventListener.disable();
    }

    @Override
    public Point getPreviewSize() {
        return mPreSize;
    }

    @Override
    public Point getPictureSize() {
        return mPicSize;
    }
}
