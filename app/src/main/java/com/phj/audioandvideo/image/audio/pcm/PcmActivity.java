package com.phj.audioandvideo.image.audio.pcm;

import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.phj.audioandvideo.R;
import com.phj.audioandvideo.base.BaseActivity;
import com.phj.audioandvideo.image.audio.pcm.play.AudioTrackManager;
import com.phj.audioandvideo.image.audio.pcm.record.AudioRecordManager;

import java.io.File;

/**
 * 描述：PCM 的采集->转换WAV->播放
 * Created by PHJ on 2019/7/9.
 */

public class PcmActivity extends BaseActivity {

    private final static String TAG = PcmActivity.class.getSimpleName();

    private Button recodeStart;
    private Button recodeStop;
    private Button trackStart;
    private Button trackStop;
    private String mFileName = Environment.getExternalStorageDirectory()+"/A/test.pcm";
    private File mFile = new File(mFileName);
    private AudioRecordManager mRecodeManager;

    @Override
    protected int getContentLayoutId() {
        return R.layout.activty_pcm;
    }

    @Override
    protected void initWidget() {
        super.initWidget();
        recodeStart = (Button) findViewById(R.id.recodeStart);
        recodeStop = (Button) findViewById(R.id.recodeStop);
        trackStart = (Button) findViewById(R.id.trackStart);
        trackStop = (Button) findViewById(R.id.trackStop);

        mRecodeManager = new AudioRecordManager(mFile, new AudioRecordManager.RecordCallback() {
            @Override
            public void onRecordStart() {
                Log.e(TAG, "onRecordStart: ");
            }

            @Override
            public void onProgress(long time) {
                Log.e(TAG, "onProgress: time = "+time);
            }

            @Override
            public void onRecordDone(File file, long time) {
                Log.e(TAG, "onRecordDone: time = "+time );
            }
        });
    }

    @Override
    protected void initData() {
        onClick();
    }


    private void onClick() {
        recodeStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecodeManager.recordAsync();
            }
        });

        recodeStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecodeManager.stop(false);
            }
        });

        trackStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioTrackManager.getInstance().startPlay(Environment.getExternalStorageDirectory()+"/A/test.wav");
            }
        });

        trackStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioTrackManager.getInstance().stopPlay();
            }
        });
    }
}
