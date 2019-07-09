package com.phj.audioandvideo.base;

import android.graphics.Point;
import android.graphics.SurfaceTexture;

/**
 * 描述：相机操作接口类
 * Created by PHJ on 2019/6/22.
 */

public interface ICamera {

    /**
     * 打开相机
     * @param cameraId 前后相机
     */
    boolean open(int cameraId);

    /**
     * 切换相机
     * @param cameraId 前后相机
     */
    boolean changeTo(int cameraId);

    /**
     * 设置预览载体
     * @param texture SurfaceTexturenb
     */
    void setPreviewTexture(SurfaceTexture texture);

    /**
     * 设置配置参数
     * @see Config
     * @param config Config
     */
    void setConfig(Config config);

    /**
     * 开始预览
     */
    void startPreview();

    /**
     * 拍照数据回调设置
     * @see TakePhotoCallback
     * @param callback TakePhotoCallback
     */
    void takePhoto(TakePhotoCallback callback);

    /**
     * 预览数据的回调
     * @see PreviewFrameCallback
     * @param callback PreviewFrameCallback
     */
    void setOnPreviewFrameCallback(PreviewFrameCallback callback);

    /**
     * 关闭预览
     */
    void close();

    /**
     * 得到预览的点
     * @return Point
     */
    Point getPreviewSize();

    /**
     * 得到拍照的点
     * @return Point
     */
    Point getPictureSize();

    /**
     *  配置
     */
    class Config {
        float rate; //宽高比
        int minPreviewWidth;
        int minPictureWidth;
    }


    // 拍照回调
    interface TakePhotoCallback{
        void onTakePhoto(byte[] bytes, int width, int height);
    }

    // 预览数据回调
    interface PreviewFrameCallback{
        void onPreviewFrame(byte[] bytes, int width, int height);
    }

}
