package com.phj.audioandvideo.audio.aac;

import android.os.Environment;
import android.view.View;

import com.phj.audioandvideo.R;
import com.phj.audioandvideo.base.BaseActivity;

public class AacActivity extends BaseActivity {

    // 视频源
    private final static String VIDEO_SOURCE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()+"/5.mp4";
    // 保存的音频文件PCM格式
    private final static String AUDIO_TARGET_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()+"/audio.pcm";

    // 保存的音频文件 AAC格式
    private final static String AUDIO_TARGET_AAC_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()+"/aac.mp3";
    private AudioDecodeManager mAudioDecodeManager;

    @Override
    protected int getContentLayoutId() {
        return R.layout.activity_aac;
    }

    @Override
    protected void initData() {
        mAudioDecodeManager = new AudioDecodeManager(VIDEO_SOURCE_PATH,AUDIO_TARGET_PATH,null);
    }

    // 编码
    public void onEncode(View view) {
        AudioEncodeManager runnable = new AudioEncodeManager(AUDIO_TARGET_PATH,AUDIO_TARGET_AAC_PATH);
        new Thread(runnable).start();
    }


    // 解码
    public void onDecode(View view) {
        mAudioDecodeManager.start();
    }
}
