package com.phj.audioandvideo.camera.texture;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.phj.audioandvideo.R;
import com.phj.audioandvideo.base.ICamera;

/**
 * TextureView作为载体，预览相机
 */
public class TextureCameraActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "TextureCameraActivity";

    private ImageView mCloseIv;
    private ImageView mSwitchCameraIv;
    private ImageView mTakePictureIv;
    private ImageView mPictureIv;
    private CameraTextureView mCameraView;

    private ICamera mCameraProxy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_texture_camera);
        initView();
    }

    private void initView() {
        mCloseIv = (ImageView) findViewById(R.id.toolbar_close_iv);
        mCloseIv.setOnClickListener(this);
        mSwitchCameraIv =  (ImageView) findViewById(R.id.toolbar_switch_iv);
        mSwitchCameraIv.setOnClickListener(this);
        mTakePictureIv = (ImageView)  findViewById(R.id.take_picture_iv);
        mTakePictureIv.setOnClickListener(this);
        mPictureIv =  (ImageView) findViewById(R.id.picture_iv);
        mPictureIv.setOnClickListener(this);
        mCameraView =  (CameraTextureView) findViewById(R.id.camera_view);
        mCameraProxy = mCameraView.getCameraProxy();

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.toolbar_close_iv:
                finish();
                break;
            case R.id.toolbar_switch_iv:

                mCameraProxy.open(1);
                mCameraProxy.setPreviewTexture(mCameraView.getSurfaceTexture());
                mCameraProxy.startPreview();
                break;
            case R.id.take_picture_iv:
                mCameraProxy.takePhoto(mPictureCallback);
                break;
            case R.id.picture_iv:
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivity(intent);
                break;
        }
    }

    private final ICamera.TakePhotoCallback mPictureCallback = new ICamera.TakePhotoCallback() {

        @Override
        public void onTakePhoto(byte[] bytes, int width, int height) {
            mCameraView.cameraToImageFile(bytes, Environment.getExternalStorageDirectory().getAbsolutePath()+"/AA.jpg",width,height);

            Log.e(TAG, "onTakePhoto: bytes="+bytes.length+" width="+width+" height="+height);
            // 继续预览
            mCameraProxy.startPreview();
        }
    };

}
