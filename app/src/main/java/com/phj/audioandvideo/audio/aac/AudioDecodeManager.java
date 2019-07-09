package com.phj.audioandvideo.audio.aac;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 描述：解码
 * 将MP4文件，获取音频流，并将解码成PCM原始数据流
 * Created by PHJ on 2019/7/3.
 */

public class AudioDecodeManager {

    private final static String TAG = "AudioDecodeManager";

    // 源流
    private String mSourcePath;
    private String mTargetPath;

    // 结果回调
    private IDecodeResultListener mListener;

    private static final long TIMEOUTUS = 10_1000;
    private final static String MIME_AUDIO = "audio/";


    AudioDecodeManager(String sourcePath, String targetPath, IDecodeResultListener listener) {
        this.mListener = listener;
        this.mSourcePath = sourcePath;
        this.mTargetPath = targetPath;

    }

    public void start() {
        Thread thread = new Thread(new AudioThread());
        thread.start();
    }

    private final class AudioThread implements Runnable {

        @Override
        public void run() {
            // 解封装
            MediaExtractor audioExtractor = new MediaExtractor();
            MediaCodec audioCodec;

            try {
                audioExtractor.setDataSource(mSourcePath);
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

            byte[] mAudioOutTempBuf = new byte[10];


            boolean isError = false;

            FileOutputStream fos;
            try {
                fos = new FileOutputStream(mTargetPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "run: FileOutputStream");
                return;
            }

            int len = 0;
            while (!Thread.interrupted()) {

                if (!isError) {
                    // 输入，用作解码
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
                        // 如果解码成功，则将解码后的音频PCM数据用AudioTrack播放出来
                        if (audioBufferInfo.size > 0) {
                            if (mAudioOutTempBuf.length < audioBufferInfo.size) {
                                mAudioOutTempBuf = new byte[audioBufferInfo.size];
                            }
                            Log.e(TAG, (++len) + "run: mAudioOutTempBuf=" + mAudioOutTempBuf.length);
                            outputBuffer.position(0);
                            outputBuffer.get(mAudioOutTempBuf, 0, audioBufferInfo.size);
                            outputBuffer.clear();
                            try {
                                fos.write(mAudioOutTempBuf, 0, audioBufferInfo.size);//数据写入文件中
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        // 释放资源
                        audioCodec.releaseOutputBuffer(outputBufferIndex, false);
                        break;
                }

                // 结尾了
                if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.e(TAG, "run: over");
                    break;
                }

            }

            try {
                fos.flush();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 释放解码器
            audioCodec.stop();
            audioCodec.release();
            audioExtractor.release();
        }
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


    interface IDecodeResultListener {

        void onDecodeSuccess();


        void onDecodeFailed();

    }

}
