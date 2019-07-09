package com.phj.audioandvideo.camera.texture;


import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;


import com.phj.audioandvideo.base.ICamera;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class CameraTextureView extends TextureView implements ICamera.PreviewFrameCallback {

    private static final String TAG = "CameraTextureView";
    private ICamera mCameraProxy;
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private float mOldDistance;

    public CameraTextureView(Context context) {
        this(context, null);
    }

    public CameraTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }


    private void init(Context context) {
        setSurfaceTextureListener(mSurfaceTextureListener);
        mCameraProxy = new CameraProxy((Activity) context);
    }

    private SurfaceTextureListener mSurfaceTextureListener = new SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mCameraProxy.open(0);
            int previewWidth = mCameraProxy.getPreviewSize().x;
            int previewHeight = mCameraProxy.getPreviewSize().y;
            if (width > height) {
                setAspectRatio(previewWidth, previewHeight);
            } else {
                setAspectRatio(previewHeight, previewWidth);
            }
            mCameraProxy.setPreviewTexture(surface);
            mCameraProxy.startPreview();
            mCameraProxy.setOnPreviewFrameCallback(CameraTextureView.this);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            mCameraProxy.close();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    public ICamera getCameraProxy() {
        return mCameraProxy;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 1) {
            // 点击聚焦
            ((CameraProxy)mCameraProxy).focusOnPoint((int) event.getX(), (int) event.getY(), getWidth(), getHeight());
            return true;
        }
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_POINTER_DOWN:
                mOldDistance = getFingerSpacing(event);
                break;
            case MotionEvent.ACTION_MOVE:
                float newDistance = getFingerSpacing(event);
                if (newDistance > mOldDistance) {
                    ((CameraProxy)mCameraProxy).handleZoom(true);
                } else if (newDistance < mOldDistance) {
                    ((CameraProxy)mCameraProxy).handleZoom(false);
                }
                mOldDistance = newDistance;
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private static float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }




    /**
     * 将相机预览的数据，转换成图片文件
     * 这里的数据是NV21格式的，YUV420格式的一种
     * @param cameraByte 相机数据
     * @param targetFilePath 保存的图片
     * @param width 相机宽度
     * @param height 相机高度
     */
    public void cameraToImageFile(byte[] cameraByte,String targetFilePath, int width, int height) {
        if (cameraByte == null || cameraByte.length <= 0 || TextUtils.isEmpty(targetFilePath)) {
            return;
        }
        File file = new File(targetFilePath);

        if (file.exists() && !file.delete()) {
            return;
        }
        try {
            if (!file.createNewFile()){
                return;
            }
        } catch (IOException e) {
            return;
        }
//        final YuvImage yuvImage = new YuvImage(cameraByte, ImageFormat.NV21, width, height, null);
//        ByteArrayOutputStream os = new ByteArrayOutputStream(cameraByte.length);
//        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, os);

//        byte[] bitmapBytes = os.toByteArray();
        final Bitmap bmp = rotateImageView(-90, BitmapFactory.decodeByteArray(cameraByte, 0, cameraByte.length));


        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, bos);

            bos.flush();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 旋转图片
     *
     * @param angle  被旋转角度
     * @param bitmap 图片对象
     * @return 旋转后的图片
     */
    private Bitmap rotateImageView(int angle, Bitmap bitmap) {
        Bitmap returnBm = null;
        // 根据旋转角度，生成旋转矩阵
        Matrix matrix = new Matrix();
        matrix.postRotate(-angle);
        try {
            // 将原始图片按照旋转矩阵进行旋转，并得到新的图片
            returnBm = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        if (returnBm == null) {
            returnBm = bitmap;
        }
        if (bitmap != returnBm) {
            bitmap.recycle();
        }
        return returnBm;
    }

    int i = 0;

    @Override
    public void onPreviewFrame(byte[] bytes, int width, int height) {
        if (i++ == 100) {
            Log.e(TAG, "onPreviewFrame: "+bytes.length+" width="+width+"  height="+height );
//            cameraToImageFile(bytes, Environment.getExternalStorageDirectory().getAbsolutePath()+"/AA.jpg",width,height);
        }

    }
}
