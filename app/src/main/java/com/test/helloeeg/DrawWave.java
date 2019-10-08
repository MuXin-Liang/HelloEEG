package com.test.helloeeg;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewDebug;

import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class DrawWave {
    private ArrayList<short[]> inBuf = new ArrayList<short[]>();
    private Random random = new Random();

    // 获取MainActivity提供的SurfaceHolder 与 Paint对象
    private SurfaceHolder holder;
    private SurfaceView surfaceView;
    private Paint paint;

    // 要绘制的曲线的高度
    private int HEIGHT;
    // 要绘制的曲线的水平宽度
    private int WIDTH;
    // 离屏幕左边界的起始距离
    private final int X_OFFSET = 5;
    // 初始化X坐标
    private int cx = X_OFFSET;
    // 实际的Y轴的位置
    private int centerY ;
    // 上一个点的位置，根据点的位置画折线
    private int startX;
    private int startY;

    private Timer timer = new Timer();
    private TimerTask task = null;
    private Context mActivity;

    public DrawWave(Context activity, SurfaceView showSurfaceView){
        mActivity = activity;
        surfaceView = showSurfaceView;

        // 初始化SurfaceHolder对象
        holder = showSurfaceView.getHolder();
        paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(3);

    }

    public void startDraw(){
        new DrawThread().start();
    }

    private void InitData() {
        Resources resources = mActivity.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        //获取SurfaceView的高度
        HEIGHT = surfaceView.getHeight();
        Log.e("Height",""+HEIGHT);
        //获取屏幕的宽度作为示波器的边长
        WIDTH = dm.widthPixels;
        //Y轴的中心就是高的一半
        centerY = HEIGHT / 2;

        // 初始化点的位置
        startX = 0;
        startY = HEIGHT/2;

    }

    public int cy = 0;
    double in;
    class DrawThread extends Thread {

        public void run() {
            InitData();
            drawBackGround(holder); //绘制背景
            if(task!= null){
                task.cancel();
            }
            task = new TimerTask() {
                @Override
                public void run() {
                    cy = (int)(in*HEIGHT/2)+HEIGHT/2;

                    Canvas canvas = holder.lockCanvas(new Rect(cx-10, cy - HEIGHT/2 ,
                            cx + 10, cy + HEIGHT/2));
                    // 根据Ｘ，Ｙ坐标画线
                    canvas.drawLine(startX, startY ,cx, cy, paint);
                    // 提交修改
                    holder.unlockCanvasAndPost(canvas);

//                    //双缓冲再画一次
//                    canvas = holder.lockCanvas(new Rect(cx-10, cy - HEIGHT/2 ,
//                            cx + 10, cy + HEIGHT/2));
//                    canvas.drawLine(startX, startY ,cx, cy, paint);
//                    holder.unlockCanvasAndPost(canvas);

                    //结束点作为下一次折线的起始点
                    startX = cx;
                    startY = cy;
                    cx+=10;

                    // 超过指定宽度，刷新
                    if (cx > WIDTH) {
                        Refresh();
                    }
                }
            };
            timer.schedule(task,0,2);

        }
    }

    private void drawBackGround(SurfaceHolder holder) {
        Canvas canvas = holder.lockCanvas();

        // 绘制黑色背景
        canvas.drawColor(Color.BLACK);
        Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setStrokeWidth(2);


        // 画网格8*8
        Paint mPaint = new Paint();
        mPaint.setColor(Color.GRAY);// 网格为黄色
        mPaint.setStrokeWidth(1);// 设置画笔粗细
        int oldY = 0;
        for (int i = 0; i <= 8; i++) {// 绘画横线
            canvas.drawLine(0, oldY, WIDTH, oldY, mPaint);
            oldY = oldY + HEIGHT/8;
        }
        int oldX = 0;
        while(oldX<WIDTH){// 绘画纵线
            canvas.drawLine(oldX, 0, oldX, HEIGHT, mPaint);
            oldX = oldX + HEIGHT/8;
        }

        // 绘制坐标轴
        canvas.drawLine(X_OFFSET, HEIGHT/2, WIDTH, HEIGHT/2, p);
        canvas.drawLine(X_OFFSET, 0, X_OFFSET, HEIGHT, p);
        holder.unlockCanvasAndPost(canvas);
        holder.lockCanvas(new Rect(0, 0, 0, 0));
        holder.unlockCanvasAndPost(canvas);
    }



    public void Refresh(){
        drawBackGround(holder);
        cx = X_OFFSET;
    }

}
