package com.phj.audioandvideo.mp4.muxer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;


import com.phj.audioandvideo.R;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * 描述：将一个mp4文件解析后，再写入到另一个文件中
 * Created by PHJ on 2019/7/2.
 */

public class MixtureActivity extends AppCompatActivity {
    private static final String TAG = "MixtureActivity";
    private static final String SDCARD_PATH = Environment.getExternalStorageDirectory().getAbsolutePath();

    // 音视频混合器
    private volatile MediaMuxer mMediaMuxer = null;

    private MediaExtractor mVMediaExtractor;
    private MediaExtractor mAMediaExtractor;

    // 音视频的流轨道
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;

    // 音频的采样率
    private int sampleRate = 0;
    // 视频的帧率
    private int frameRate = 0;

    // 栅栏 作为释放混淆器的拦截方法
    private CyclicBarrier barrier = new CyclicBarrier(2);

    private final Object object = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 获取权限
        int checkWriteExternalPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int checkReadExternalPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);if (checkWriteExternalPermission != PackageManager.PERMISSION_GRANTED ||
                checkReadExternalPermission != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }

        setContentView(R.layout.activity_main);

    }


    @Override
    protected void onResume() {
        super.onResume();
        mVMediaExtractor = new MediaExtractor();
        mAMediaExtractor = new MediaExtractor();
        try {
            mMediaMuxer = new MediaMuxer(SDCARD_PATH + "/ouput.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mAMediaExtractor.setDataSource(SDCARD_PATH + "/5.mp4");
            mVMediaExtractor.setDataSource(SDCARD_PATH + "/5.mp4");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


        for (int i = 0; i < mAMediaExtractor.getTrackCount(); i++) {
            MediaFormat format = mAMediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!mime.startsWith("audio/")) {
                continue;
            }
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            mAMediaExtractor.selectTrack(i);
            mAudioTrackIndex = mMediaMuxer.addTrack(format);
            Log.e(TAG, "processAudio: mAudioTrackIndex="+mAudioTrackIndex);

        }



        for (int i = 0; i < mVMediaExtractor.getTrackCount(); i++) {
            MediaFormat format = mVMediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!mime.startsWith("video/")) {
                continue;
            }
            frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
            mVMediaExtractor.selectTrack(i);
            mVideoTrackIndex = mMediaMuxer.addTrack(format);

            Log.e(TAG, "processVideo: mVideoTrackIndex="+mVideoTrackIndex );
        }

        if (mVideoTrackIndex == -1 || mAudioTrackIndex == -1)
            return;

        mMediaMuxer.start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                processVideo();
                Log.e("TAG", "processVideo run: end" );
            }
        }).start();


        new Thread(new Runnable() {
            @Override
            public void run() {
                processAudio();
                Log.e("TAG", "processAudio run: end" );
            }
        }).start();
    }






    private void processAudio() {

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        info.presentationTimeUs = 0;
        ByteBuffer buffer = ByteBuffer.allocate(500 * 1024);
        int sampleSize;
        while ((sampleSize = mAMediaExtractor.readSampleData(buffer, 0)) > 0) {

            info.offset = 0;
            info.size = sampleSize;
            info.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
            info.presentationTimeUs = mAMediaExtractor.getSampleTime();
            synchronized (object) {
                mMediaMuxer.writeSampleData(mAudioTrackIndex, buffer, info);
            }

            mAMediaExtractor.advance();
        }

        mAMediaExtractor.release();

        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
    }

    private void processVideo() {

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        info.presentationTimeUs = 0;
        ByteBuffer buffer = ByteBuffer.allocate(500 * 1024);
        int sampleSize;
        while ((sampleSize = mVMediaExtractor.readSampleData(buffer, 0)) > 0) {

            info.offset = 0;
            info.size = sampleSize;
            info.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
            info.presentationTimeUs += 1000 * 1000 / frameRate;

            synchronized (object) {
                mMediaMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
            }

            mVMediaExtractor.advance();
        }

        mVMediaExtractor.release();

        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
        mMediaMuxer.stop();
        mMediaMuxer.release();
    }
}
