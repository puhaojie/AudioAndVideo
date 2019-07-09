package com.phj.audioandvideo.audio.pcm.play;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * 使用AudioTrack播放PCM音频
 */
public class AudioTrackManager {

    private final static String TAG = AudioTrackManager.class.getSimpleName();
    private AudioTrack mAudioTrack;
    private DataInputStream mDis;//播放文件的数据流
    private Thread mRecordThread;
    private volatile static AudioTrackManager mInstance;

    //音频流类型
    private static final int mStreamType = AudioManager.STREAM_MUSIC;
    //指定采样率 （MediaRecoder 的采样率通常是8000Hz AAC的通常是44100Hz。 设置采样率为44100，目前为常用的采样率，官方文档表示这个值可以兼容所有的设置）
    private static int mSampleRateInHz = 44100;
    //指定捕获音频的声道数目。在AudioFormat类中指定用于此的常量
    private static final int mChannelConfig = AudioFormat.CHANNEL_IN_STEREO; //单声道
    //指定音频量化位数 ,在AudioFormaat类中指定了以下各种可能的常量。通常我们选择ENCODING_PCM_16BIT和ENCODING_PCM_8BIT PCM代表的是脉冲编码调制，它实际上是原始音频样本。
    //因此可以设置每个样本的分辨率为16位或者8位，16位将占用更多的空间和处理能力,表示的音频也更加接近真实。
    private static int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    //指定缓冲区大小。调用AudioRecord类的getMinBufferSize方法可以获得。
    private int mMinBufferSize;
    //STREAM的意思是由用户在应用程序通过write方式把数据一次一次得写到audiotrack中。这个和我们在socket中发送数据一样，
    // 应用层从某个地方获取数据，例如通过编解码得到PCM数据，然后write到audiotrack。
    private static int mMode = AudioTrack.MODE_STREAM;


    private AudioTrackManager() {

    }

    private void initData() {
        //根据采样率，采样精度，单双声道来得到frame的大小。
        mMinBufferSize = AudioTrack.getMinBufferSize(mSampleRateInHz, mChannelConfig, mAudioFormat) / 2;//计算最小缓冲区
        //注意，按照数字音频的知识，这个算出来的是一秒钟buffer的大小。
        //创建AudioTrack
        mAudioTrack = new AudioTrack(mStreamType, mSampleRateInHz, mChannelConfig,
                mAudioFormat, mMinBufferSize, mMode);
    }


    /**
     * 获取单例引用
     *
     * @return AudioTrackManager
     */
    public static AudioTrackManager getInstance() {
        if (mInstance == null) {
            synchronized (AudioTrackManager.class) {
                if (mInstance == null) {
                    mInstance = new AudioTrackManager();
                }
            }
        }
        return mInstance;
    }

    /**
     * 销毁线程方法
     */
    private void destroyThread() {
        try {
            if (null != mRecordThread && Thread.State.RUNNABLE == mRecordThread.getState()) {
                try {
                    mRecordThread.interrupt();
                } catch (Exception e) {
                    mRecordThread = null;
                }
            }
            mRecordThread = null;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mRecordThread = null;
        }
    }

    /**
     * 启动播放线程
     */
    private void startThread() {
        destroyThread();
        if (mRecordThread == null) {
            mRecordThread = new Thread(recordRunnable);
            mRecordThread.start();
        }
    }

    /**
     * 播放线程
     */
    private final Runnable recordRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                //设置线程的优先级
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                byte[] tempBuffer = new byte[mMinBufferSize];
                int readCount;
                while (mDis.available() > 0) {
                    readCount = mDis.read(tempBuffer);
                    if (readCount == AudioTrack.ERROR_INVALID_OPERATION || readCount == AudioTrack.ERROR_BAD_VALUE) {
                        continue;
                    }
                    if (readCount != 0 && readCount != -1) {//一边播放一边写入语音数据
                        //判断AudioTrack未初始化，停止播放的时候释放了，状态就为STATE_UNINITIALIZED
                        if (mAudioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
                            initData();
                        }
                        mAudioTrack.play();
                        mAudioTrack.write(tempBuffer, 0, readCount);
                    }
                }
                stopPlay();//播放完就停止播放
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    };

    /**
     * 播放文件
     *
     * @param path 播放的音频路径
     * @throws Exception Exception
     */
    private void setPath(String path) throws Exception {
        File file = new File(path);
        mDis = new DataInputStream(new FileInputStream(file));
        if (format()) {
            initData();
        } else {
            mDis = new DataInputStream(new FileInputStream(file));
        }

    }

    /**
     * 分析格式 是否是WAV格式
     */
    private boolean format() {
        byte[] header = new byte[44];
        boolean isWavFormat = false;
        if (mDis != null) {
            try {
                int len = mDis.read(header, 0, header.length);
                if (len < header.length)
                    return false;
                if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F')
                    return false;
                if (header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E')
                    return false;
                isWavFormat = true;

                // 读取通道数等信息

                // 1、采样率
                int channels = ((((int) header[23]) & 0xFF) << 8) | (((int) header[22]) & 0xFF);
                Log.e(TAG, "format: channels=" + channels);

                mSampleRateInHz = ((((int) header[27]) & 0xFF) << 24) | ((((int) header[26]) & 0xFF) << 16)
                        | ((((int) header[25]) & 0xFF) << 8) | (((int) header[24]) & 0xFF);
                Log.e(TAG, "format: sampleRate=" + mSampleRateInHz);

                mAudioFormat = (((((int) header[35]) & 0xFF) << 8) | (((int) header[34]) & 0xFF)) / 8;
                Log.e(TAG, "format: sampleFormat=" + mAudioFormat);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return isWavFormat;
    }

    /**
     * 启动播放
     *
     * @param path 音频路径
     */
    public void startPlay(String path) {
        try {
            setPath(path);
            startThread();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止播放
     */
    public void stopPlay() {
        try {
            destroyThread();//销毁线程
            if (mAudioTrack != null) {
                if (mAudioTrack.getState() == AudioRecord.STATE_INITIALIZED) {//初始化成功
                    mAudioTrack.stop();//停止播放
                }
                if (mAudioTrack != null) {
                    mAudioTrack.release();//释放audioTrack资源
                }
            }
            if (mDis != null) {
                mDis.close();//关闭数据输入流
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
