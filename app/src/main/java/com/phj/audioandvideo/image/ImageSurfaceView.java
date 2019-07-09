package com.phj.audioandvideo.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.phj.audioandvideo.R;


/**
 * 描述：使用SurfaceView在显示一张图片
 * Created by PHJ on 2019/7/9.
 */

public class ImageSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private final static String TAG = ImageSurfaceView.class.getSimpleName();
    private SurfaceHolder mHolder;

    public ImageSurfaceView(Context context) {
        this(context,null);
    }

    public ImageSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHolder = getHolder();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "surfaceCreated: ");
        Canvas canvas = holder.lockCanvas();
        Paint paint =  new Paint();
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        Rect dstRect = new Rect(0,0,getWidth(),getHeight());
        paint.setColor(Color.WHITE);
        canvas.drawRect(dstRect,paint);
        canvas.drawBitmap(bitmap,null,dstRect,paint);
        holder.unlockCanvasAndPost(canvas);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

}
