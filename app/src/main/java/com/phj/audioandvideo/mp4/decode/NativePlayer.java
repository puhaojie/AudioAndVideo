package com.phj.audioandvideo.mp4.decode;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 描述：MediaExtractor原生播放器
 * Created by PHJ on 2019/7/1.
 */

public class NativePlayer {

    private final static String TAG = NativePlayer.class.getSimpleName();

    private final static String MIME_VIDEO = "video/";
    private final static String MIME_AUDIO = "audio/";

    // 读取等操作时间
    private static final long TIMEOUTUS = 10000;

    private Runnable mVideoThread = new VideoThread();
    private Runnable mAudioThread = new AudioThread();

    private MediaCodec videoCodec;

    private volatile boolean isPlaying = false;
    private volatile boolean isPause = false;
    private volatile boolean isStop = false;

    private String mPath;

    // 数据渲染载体
    private Surface mSurface;

    public NativePlayer(Surface surface) {
        this.mSurface = surface;
    }

    public void setPath(String path) {
        if (TextUtils.isEmpty(path))
            throw new IllegalArgumentException("file's path is empty");
        this.mPath = path;
    }

    public void stop() {
        this.isStop = true;
    }

    public void start() {
        if ((isPlaying || isPause) && !isStop) {
            return;
        }
        this.isPlaying = true;
        Thread vThread = new Thread(mVideoThread);
        Thread aThread = new Thread(mAudioThread);

        vThread.start();
        aThread.start();
    }

    // 暂停的时候该视频不能够正常的同步
    public void pause() {
        if (isPause) {
            return;
        }
        this.isPause = true;
    }

    public void continuePlay() {
        if (!isPause) {
            return;
        }
        this.isPause = false;
    }

    public boolean isStop() {
       return isStop;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setSurface(Surface mSurface) {
        this.mSurface = mSurface;
        if (videoCodec != null) {
            videoCodec.setOutputSurface(mSurface);
        }
    }

    private final class VideoThread implements Runnable {

        @Override
        public void run() {
            // 解封装
            MediaExtractor videoExtractor = new MediaExtractor();


            try {
                videoExtractor.setDataSource(mPath);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "mPath error");
                return;
            }

            //1、获取流索引（音频、视频等），用于解封装解码
            int streamIndex = getTrackIndex(videoExtractor, MIME_VIDEO);
            if (streamIndex == -1) {
                throw new RuntimeException("getTrackIndex error");
            }

            // 2、解封装
            videoExtractor.selectTrack(streamIndex);

            // 3、解码器
            MediaFormat mediaFormat = videoExtractor.getTrackFormat(streamIndex);
            // 创建解码器
            try {
                videoCodec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
                // 配置解码器

                videoCodec.configure(mediaFormat, mSurface, null, 0);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("create videoCodec is error");
            }

            // 4、解码
            videoCodec.start();
            // 存储解码出的数据相关参数
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer[] inputBuffers = videoCodec.getInputBuffers();

            boolean isError = false;
            long startMs = System.currentTimeMillis();
            while (!Thread.interrupted() && !isStop) {
                // 暂停的时候释放CPU
                while (isPause) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


                if (!isError) {
                    isError = decodeMediaData(videoExtractor, videoCodec, inputBuffers);
                }

                // 取出数据
                /*
                 * @link MediaCodec
                 */
                int outputBufferIndex = videoCodec.dequeueOutputBuffer(videoBufferInfo, TIMEOUTUS);


                switch (outputBufferIndex) {

                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        // 需要替换
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        break;
                    default:
                        // 延迟解码
                        decodeDelay(videoBufferInfo, startMs);
                        // 释放资源
                        videoCodec.releaseOutputBuffer(outputBufferIndex, true);
                        break;
                }


                // 结尾了
                if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
            isStop = true;
            // 释放解码器
            videoCodec.stop();
            videoCodec.release();
            videoExtractor.release();
        }
    }

    /**
     * 推迟显示
     * @param bufferInfo BufferInfo，获取视频显示的时间差
     * @param startMs startMs 当前时间差
     */
    private void decodeDelay(MediaCodec.BufferInfo bufferInfo, long startMs) {
        while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 真正的解封装解码数据,数据并不暴露出来
     *
     * @param extractor    MediaExtractor
     * @param mediaCodec   MediaCodec
     * @param inputBuffers inputBuffer
     * @return true 读取错误或者结束
     */
    private boolean decodeMediaData(MediaExtractor extractor, MediaCodec mediaCodec, ByteBuffer[] inputBuffers) {
        boolean isErrorOrEnd = false;
        // 对应哪个inputBuffer
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(TIMEOUTUS);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            int readSize = extractor.readSampleData(inputBuffer, 0);
            if (readSize <= 0) {
                // 读取的数据问题
                // 提取
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                isErrorOrEnd = true;
            } else {
                long sampleTime = extractor.getSampleTime();
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, readSize, sampleTime, 0);
                // 读取下一个
                extractor.advance();
            }
        }
        return isErrorOrEnd;
    }

    /**
     * 获取mediaType对应的流索引
     *
     * @param extractor MediaExtractor {@link MediaExtractor}
     * @param mediaType String {@link MediaFormat}
     * @return index
     */
    private int getTrackIndex(MediaExtractor extractor, String mediaType) {
        int trackIndex = -1;
        int count = extractor.getTrackCount();
        for (int index = 0; index < count; index++) {
            MediaFormat format = extractor.getTrackFormat(index);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith(mediaType)) {
                trackIndex = index;
                break;
            }
        }

        return trackIndex;
    }


    private final class AudioThread implements Runnable {

        @Override
        public void run() {
            // 解封装
            MediaExtractor audioExtractor = new MediaExtractor();
            MediaCodec audioCodec;

            try {
                audioExtractor.setDataSource(mPath);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "mPath error");
                return;
            }

            //1、获取流索引（音频、视频等），用于解封装解码
            int streamIndex = getTrackIndex(audioExtractor, MIME_AUDIO);
            if (streamIndex == -1) {
                throw new RuntimeException("getTrackIndex error");
            }

            // 2、解封装
            audioExtractor.selectTrack(streamIndex);

            // 3、解码器
            MediaFormat mediaFormat = audioExtractor.getTrackFormat(streamIndex);
            // 通道
            int audioChannels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            // 采样率
            int audioSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);

            int minBufferSize = AudioTrack.getMinBufferSize(audioSampleRate,
                    (audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                    AudioFormat.ENCODING_PCM_16BIT);
            int maxInputSize = mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            int mInputBufferSize = minBufferSize > 0 ? minBufferSize * 4 : maxInputSize;
            int frameSizeInBytes = audioChannels * 2;
            mInputBufferSize = (mInputBufferSize / frameSizeInBytes) * frameSizeInBytes;
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    audioSampleRate,
                    (audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                    AudioFormat.ENCODING_PCM_16BIT,
                    mInputBufferSize,
                    AudioTrack.MODE_STREAM);
            audioTrack.play();
            // 创建解码器
            try {
                audioCodec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
                // 配置解码器
                audioCodec.configure(mediaFormat, null, null, 0);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("create videoCodec is error");
            }

            // 4、解码
            audioCodec.start();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer[] inputBuffers = audioCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = audioCodec.getOutputBuffers();
            int sz = outputBuffers[0].capacity();
            if (sz <= 0) {
                sz = mInputBufferSize;
            }
            byte[] mAudioOutTempBuf = new byte[sz];



            boolean isError = false;
            long startMs = System.currentTimeMillis();

            while (!Thread.interrupted() && !isStop) {

                // 暂停的时候释放CPU
                while (isPause) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (!isError) {
                    isError = decodeMediaData(audioExtractor, audioCodec, inputBuffers);
                }

                // 取出数据
                /*
                 * @link MediaCodec
                 */
                int outputBufferIndex = audioCodec.dequeueOutputBuffer(audioBufferInfo, TIMEOUTUS);

                switch (outputBufferIndex) {

                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        // 需要替换
                        outputBuffers = audioCodec.getOutputBuffers();
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        break;
                    default:
                        ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                        // 延时解码，跟视频时间同步
                        decodeDelay(audioBufferInfo, startMs);
                        // 如果解码成功，则将解码后的音频PCM数据用AudioTrack播放出来
                        if (audioBufferInfo.size > 0) {
                            if (mAudioOutTempBuf.length < audioBufferInfo.size) {
                                mAudioOutTempBuf = new byte[audioBufferInfo.size];
                            }
                            outputBuffer.position(0);
                            outputBuffer.get(mAudioOutTempBuf, 0, audioBufferInfo.size);
                            outputBuffer.clear();
                            audioTrack.write(mAudioOutTempBuf, 0, audioBufferInfo.size);
                        }
                        // 释放资源
                        audioCodec.releaseOutputBuffer(outputBufferIndex, false);
                        break;
                }

                // 结尾了
                if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }

            }
            isStop = true;
            // 释放解码器
            audioCodec.stop();
            audioCodec.release();
            audioExtractor.release();
            audioTrack.stop();
            audioTrack.release();
        }
    }
}
