package jp.takke.datastats;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayDeque;
import java.util.LinkedList;

import jp.takke.util.MyLog;

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final long TARGET_FPS = 30;

    private SurfaceHolder mSurfaceHolder;
    private Thread mThread;

    private int mScreenWidth;
    @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
    private int mScreenHeight;

    private ArrayDeque<Long> mDrawTimes = new ArrayDeque<>();

    private class Traffic {

        public long time;
        public long tx;
        public int pTx;
        public long rx;
        public int pRx;

        public Traffic(long time, long tx, int pTx, long rx, int pRx) {
            this.time = time;
            this.tx = tx;
            this.pTx = pTx;
            this.rx = rx;
            this.pRx = pRx;
        }
    }

    private LinkedList<Traffic> mTrafficList = new LinkedList<>();
    private static final int TRAFFIC_LIST_COUNT_MAX = 3;

    private Bitmap mDownloadMarkBitmap;
    private Bitmap mUploadMarkBitmap;
    private Drawable uploadDrawable;
    private Drawable downloadDrawable;
    
    // 補間モード
    private boolean mInterpolateMode = true;

    private int mLastPTx;
    private int mLastPRx;

    public MySurfaceView(Context context) {
        super(context);

        init();
    }
    
    
    public MySurfaceView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        
        init();
    }


    private void init() {

        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);

        // make this surfaceview transparent
        mSurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        setFocusable(true);
        setZOrderOnTop(true);
    }


    public void setTraffic(long tx, int pTx, long rx, int pRx) {

        mTrafficList.add(new Traffic(System.currentTimeMillis(), tx, pTx, rx, pRx));
        
        // 過去データ削除
        if (mTrafficList.size() > TRAFFIC_LIST_COUNT_MAX) {
            final int n = mTrafficList.size() - TRAFFIC_LIST_COUNT_MAX;
            for (int i = 0; i < n; i++) {
                mTrafficList.removeFirst();
            }
        }
        
        if (!mInterpolateMode) {
            myDraw();
        }
    }
    
    
    @Override
    public void run() {

        while (mThread != null) {

            myDraw();
        }

    }


    private void myDraw() {

        final long startTime = System.currentTimeMillis();

        try {
            myDrawFrame(startTime);
        } catch (Exception ignored) {
            MyLog.e(ignored);
        }

        final long now = System.currentTimeMillis();
        long waitMs = 1000 / TARGET_FPS - (now - startTime);
        if (waitMs < 0) {
            waitMs = 1;
        }
        SystemClock.sleep(waitMs);
        
        // for actual FPS
        mDrawTimes.addLast(now);
        if (mDrawTimes.size() > 24) {
            final int n = mDrawTimes.size() - 24;
            for (int i=0; i<n; i++) {
                mDrawTimes.removeFirst();
            }
        }
    }


    private void myDrawFrame(@SuppressWarnings("UnusedParameters") long startTime) {

        final Canvas canvas = mSurfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }
        
        final Paint paint = new Paint();

        // clear
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // Background
        final Resources resources = getResources();
        canvas.drawColor(resources.getColor(R.color.textBackgroundColor));

        //--------------------------------------------------
        // upload, download
        //--------------------------------------------------
        final int udMarkSize = resources.getDimensionPixelSize(R.dimen.ud_mark_size);
        final int paddingLeft = resources.getDimensionPixelSize(R.dimen.myOverlayPaddingLeft);
        final int paddingRight = resources.getDimensionPixelSize(R.dimen.myOverlayPaddingRight);
        
        final int xDownloadStart = mScreenWidth / 2;

        if (mTrafficList.size() >= 1) {
            final Traffic t = mTrafficList.getLast();
            final long now = System.currentTimeMillis();
            
            // upload gradient
            if (uploadDrawable == null) {
                uploadDrawable = resources.getDrawable(R.drawable.upload_background);
            }
            final int pTx = mInterpolateMode ? interpolate(t, now, true) : t.pTx;
            mLastPTx = pTx;
            uploadDrawable.setBounds(0, 0, (int) (pTx / 1000f * xDownloadStart), mScreenHeight);
            uploadDrawable.draw(canvas);
            
            // download gradient
            if (downloadDrawable == null) {
                downloadDrawable = resources.getDrawable(R.drawable.download_background);
            }
            final int pRx = mInterpolateMode ? interpolate(t, now, false) : t.pRx;
            mLastPRx = pRx;
            downloadDrawable.setBounds(xDownloadStart, 0, (int) (xDownloadStart + pRx / 1000f * xDownloadStart), mScreenHeight);
            downloadDrawable.draw(canvas);

            // upload text
            final long tx = t.tx;
            paint.setTypeface(Typeface.MONOSPACE);
            paint.setColor(MyTrafficUtil.getTextColorByBytes(resources, tx));
            paint.setShadowLayer(1.5f, 1.5f, 1.5f, MyTrafficUtil.getTextShadowColorByBytes(resources, tx));
            final String u = MyTrafficUtil.convertByteToKb(tx) + "." + MyTrafficUtil.convertByteToD1Kb(tx) + "KB/s";
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(resources.getDimensionPixelSize(R.dimen.textSize));
            canvas.drawText(u, xDownloadStart-paddingRight, paint.getTextSize(), paint);

            // download text
            final long rx = t.rx;
            paint.setTypeface(Typeface.MONOSPACE);
            paint.setColor(MyTrafficUtil.getTextColorByBytes(resources, rx));
            paint.setShadowLayer(1.5f, 1.5f, 1.5f, MyTrafficUtil.getTextShadowColorByBytes(resources, rx));
            final String d = MyTrafficUtil.convertByteToKb(rx) + "." + MyTrafficUtil.convertByteToD1Kb(rx) + "KB/s";
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(resources.getDimensionPixelSize(R.dimen.textSize));
            canvas.drawText(d, mScreenWidth - paddingRight, paint.getTextSize(), paint);
            
            paint.setShader(null);
        }

        // upload/download mark
        final Paint paintUd = new Paint();
        {
            if (mUploadMarkBitmap == null) {
                mUploadMarkBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_find_previous_holo_dark);
            }
            final Matrix matrix = new Matrix();
            final float s = (float) (udMarkSize - paddingLeft) / mUploadMarkBitmap.getWidth();
            matrix.setScale(s, s);
            matrix.postTranslate(paddingLeft, 0);
            canvas.drawBitmap(mUploadMarkBitmap, matrix, paintUd);
        }
        {
            if (mDownloadMarkBitmap == null) {
                mDownloadMarkBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_find_next_holo_dark);
            }
            final Matrix matrix = new Matrix();
            final float s = (float) (udMarkSize - paddingLeft) / mDownloadMarkBitmap.getWidth();
            matrix.setScale(s, s);
            matrix.postTranslate(xDownloadStart + paddingLeft, 0);
            canvas.drawBitmap(mDownloadMarkBitmap, matrix, paintUd);
        }

        // FPS
//        {
//            paint.setColor(Color.rgb(0x80, 0x80, 0x80));
//            paint.setTextSize(20);
//            paint.setTextAlign(Paint.Align.RIGHT);
//            final float fps = calcCurrentFps(startTime);
//            final long now = System.currentTimeMillis();
//            canvas.drawText(((int) fps) + "." + (int) ((fps * 10) % 10) + "[fps] " + (now - startTime) + "ms",
//                    mScreenWidth-paddingRight, mScreenHeight-10, paint);
//        }


        mSurfaceHolder.unlockCanvasAndPost(canvas);
    }


    private int interpolate(Traffic t, long now, boolean getTx) {

        final int currentP = getTx ? t.pTx : t.pRx;
        
        final int n = mTrafficList.size() - 1;
        if (n < 2) {
            return currentP;
        } else {

            // 最後の2つ分の差分時間を補間に使う
            final long lastIntervalTime = mTrafficList.get(n).time - mTrafficList.get(n-1).time;
            final long elapsed = now - mTrafficList.get(n).time;
            if (elapsed > lastIntervalTime * 3) {
                // 十分時間が経過しているので収束させる
                return currentP;
            } else {
                
                // 補間準備
                final double[] x = new double[n + 1];
                final double[] y = new double[n + 1];
                for (int i = 0; i < n + 1; i++) {
                    final Traffic t1 = mTrafficList.get(i);
                    x[i] = t1.time;
                    y[i] = getTx ? t1.pTx : t1.pRx;
                }

                // 要は lastIntervalTime 分だけ遅れて描画される感じ
                // でも lastIntervalTime だと遅すぎるので少し短くしておく
                final long at = now - lastIntervalTime / 2;
                final int p = (int) lagrange(n, x, y, at);
                if (p < 0) {
                    return 0;
                } else if (p > 1000) {
                    return 1000;
                }
                
                // 前回の差分から方向を算出し、現在値を超えていたら超えないようにする
                final int lastP = getTx ? mLastPTx : mLastPRx;
                final int direction = p - lastP;
                if (direction > 0 && p > currentP) {
                    // 上昇方向で超過しているので制限する
                    return currentP;
                } else if (direction < 0 && p < currentP) {
                    // 下降方向で下回っているので制限する
                    return currentP;
                }
                
                return p;
            }
        }
    }


    private double lagrange(int n, double[] x, double[] y, double x1) {

        double s1, s2, y1 = 0.0;
        int i1, i2;

        for (i1 = 0; i1 <= n; i1++) {
            s1 = 1.0;
            s2 = 1.0;
            for (i2 = 0; i2 <= n; i2++) {
                if (i2 != i1) {
                    s1 *= (x1 - x[i2]);
                    s2 *= (x[i1] - x[i2]);
                }
            }
            y1 += y[i1] * s1 / s2;
        }

        return y1;
    }


    @SuppressWarnings("UnusedDeclaration")
    private float calcCurrentFps(final long now) {
        if (mDrawTimes.size() <= 0) {
            return 1;
        }
        final long firstDrawTime = mDrawTimes.getFirst();
        return (1000f * mDrawTimes.size() / (now - firstDrawTime));
    }
    
    
    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        MyLog.d("MySurfaceView.surfaceCreated");

        if (mInterpolateMode) {
            mThread = new Thread(this);
        }
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        MyLog.d("MySurfaceView.surfaceChanged[" + width + "," + height + "]");

        mScreenWidth = width;
        mScreenHeight = height;
        
        if (mThread != null) {
            mThread.start();
        }
    }


    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        MyLog.d("MySurfaceView.surfaceDestroyed");
        
        mThread = null;
    }
}
