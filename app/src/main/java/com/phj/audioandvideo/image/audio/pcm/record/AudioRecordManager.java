package com.phj.audioandvideo.image.audio.pcm.record;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 使用AudioRecord采集声音
 * Created by Administrator on 2019/6/3.
 */

public class AudioRecordManager {

    // 打印日志使用
    private static final String TAG = AudioRecordManager.class.getSimpleName();
    // 采样频率集合，用于适应不同手机情况
    private static final int[] SAMPLE_RATES = new int[]{44100, 22050, 11025, 8000};
    // 状态回调
    private RecordCallback callback;
    // 缓存文件，无论那一个录音都复用同一个缓存文件
    private File tmpFile;
    // 进行初始化时需要的buffer大小, 通过AudioRecord.getMinBufferSize运算得到
    // AudioRecord.getMinBufferSize得到的是bytes的大小；而我们读取的时候是short
    // 所以需要该值为AudioRecord.getMinBufferSize/2
    private int minShortBufferSize;
    // 录制完成
    private boolean isDone;
    // 是否取消
    private volatile boolean isCancel;

    /**
     * 构造函数
     *
     * @param tmpFile  缓存文件
     * @param callback 录制的状态回调
     */
    public AudioRecordManager(File tmpFile, RecordCallback callback) {
        this.tmpFile = tmpFile;
        this.callback = callback;
    }

    /**
     * 初始化一个录音器
     *
     * @return 返回一个录音器
     */
    private AudioRecord initAudioRecord() {
        // 遍历采样频率
        for (int rate : SAMPLE_RATES) {
            // 编码比特率
            for (short audioFormat : new short[]{AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_8BIT}) {
                // 录音通道：双通道，单通道
                for (short channelConfig : new short[]{AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO}) {
                    try {

                        // 尝试获取最小的缓存区间大小
                        int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);
                        Log.d(TAG, "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
                                + channelConfig+" bufferSize="+bufferSize);
                        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                            // 如果初始化成功
                            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, channelConfig, audioFormat, bufferSize);
                            // 尝试进行构建
                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                                // 在后面的使用中我们使用的类型是short，得到的是byte类型的缓冲区间大小
                                // 所以是其一般的大小即可，很多人不注意这一点很容易导致多余内存消耗
                                minShortBufferSize = bufferSize / 2;
                                return recorder;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, rate + "Exception, keep trying.", e);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 初始化缓存的文件
     * 文件已经存在则重新进行覆盖新文件
     *
     * @return 新的文件
     */
    private File initTmpFile() {
        if (tmpFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tmpFile.delete();
        }
        try {
            if (tmpFile.createNewFile())
                return tmpFile;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * WAV格式的音频头输出
     * @param fos 输出流
     * @param pcmDataLength pcm原始格式的数据大小
     * @param sampleFormat 存储bit位数
     * @param sampleRate 每秒的采样率
     * @param channels 通道数
     * @throws IOException 异常
     */
    private void writeWavHeader(@NonNull FileOutputStream fos, long pcmDataLength, int sampleFormat,
                                int sampleRate, int channels) throws IOException {
        long audioDataLength = pcmDataLength + 36;
        long bitRate = sampleRate * channels * sampleFormat / 8;
        byte[] header = new byte[44];
        // 0~3 魔数
        // RIFF
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        // pcm data length
        // 4~7原始数据的长度（小端模式）
        header[4] = (byte) (pcmDataLength & 0xff);
        header[5] = (byte) ((pcmDataLength >> 8) & 0xff);
        header[6] = (byte) ((pcmDataLength >> 16) & 0xff);
        header[7] = (byte) ((pcmDataLength >> 24) & 0xff);
        // WAVE
        // 8~11 协议名称
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        // 'fmt '
        // h12~15  特有的
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';

        // 16~19 有无附加字节
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;

        // 1(PCM)
        // 20~21格式 pcm=1
        header[20] = 1;
        header[21] = 0;

        // channels
        // 通道数
        header[22] = (byte) channels;
        header[23] = 0;

        // sample rate
        // 24~27 采样率
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);

        // bit rate
        // 传输速率
        header[28] = (byte) (bitRate & 0xff);
        header[29] = (byte) ((bitRate >> 8) & 0xff);
        header[30] = (byte) ((bitRate >> 16) & 0xff);
        header[31] = (byte) ((bitRate >> 24) & 0xff);

        // PCM 位宽
        header[32] = 4;
        header[33] = 0;

        // 采样精度
        header[34] = (byte) sampleFormat;
        header[35] = 0;

        // data
        // 数据标志
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';

        // data length
        // 总数据长度
        header[40] = (byte) (audioDataLength & 0xff);
        header[41] = (byte) ((audioDataLength >> 8) & 0xff);
        header[42] = (byte) ((audioDataLength >> 16) & 0xff);
        header[43] = (byte) ((audioDataLength >> 24) & 0xff);
        fos.write(header);
    }

    /**
     * 进行异步录制
     */
    public void recordAsync() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    record();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    /**
     * 进行同步录制
     *
     * @return 录制完成后返回一个文件，文件就是缓存的文件
     */
    public File record() throws IOException {
        isCancel = false;
        isDone = false;

        // 开始进行初始化
        AudioRecord audioRecorder;
        File file;
        Log.e(TAG, "record: "+System.currentTimeMillis() );
        if ((audioRecorder = initAudioRecord()) == null
                || (file = initTmpFile()) == null) {
            return null;
        }
        Log.e(TAG, "record: "+System.currentTimeMillis() );
        // 初始化输出到文件的流
        BufferedOutputStream outputStream;
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        final int shortBufferSize = minShortBufferSize;
        final RecordCallback callback = this.callback;

        // 定义缓冲
        byte[] buffer = new byte[8*1024];

        int readSize;
        long endTime;

        // 通知开始
        audioRecorder.startRecording();
        callback.onRecordStart();
        // 记录开始的时间
        final long startTime = SystemClock.uptimeMillis();

        // 在当前线程中循环的读取系统录制的用户音频
        while (true) {

            // 开始进行读取
            readSize = audioRecorder.read(buffer, 0, shortBufferSize);
            // 如果读取成功
            if (AudioRecord.ERROR_INVALID_OPERATION != readSize) {
                // 那么把读取成功的数据，push到异步转码器Lame中，进行异步的处理
                outputStream.write(buffer,0,readSize);
            }

            // 回调进度
            endTime = SystemClock.uptimeMillis();
            callback.onProgress(endTime - startTime);

            // 如果没有完成标示则继续录制
            if (isDone) {
                break;
            }
        }

        // 进行录制完成
        audioRecorder.stop();
        // 释放录制器
        audioRecorder.release();
        outputStream.close();

        // 如果说不是取消，则通知回调
        if (!isCancel) {
            File f = new File(file.getParentFile().getAbsoluteFile()+"/test.wav");

            if (!f.exists() && f.createNewFile())
            {
                FileOutputStream fops = new FileOutputStream(f);
                FileInputStream fis = new FileInputStream(file);
                writeWavHeader(fops,file.length(),16,44100,2);
                byte[] data = new byte[1024];
                while (fis.read(data) != -1) {
                    fops.write(data);
                }
                fis.close();
                fops.close();
                callback.onRecordDone(file, endTime - startTime);
            }
        }

        // 返回文件
        return file;
    }

    /**
     * 停止录制语音
     * 传递一个参数标示是取消录音还是完成录音
     *
     * @param isCancel True 则代表想要取消录音；False 则代表正常完成录音
     */
    public void stop(boolean isCancel) {
        this.isCancel = isCancel;
        this.isDone = true;
    }

    /**
     * 录制的回调
     */
    public interface RecordCallback {
        // 录制开始的回调
        void onRecordStart();

        // 回调进度，当前的时间
        void onProgress(long time);

        // 录制完成的回调，如果停止录制时传递的是取消，那么则不会回调该方法
        void onRecordDone(File file, long time);
    }
}
