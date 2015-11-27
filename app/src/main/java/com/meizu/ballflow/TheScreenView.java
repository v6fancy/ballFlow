package com.meizu.ballflow;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

/**
 * 自定义的View类,为两个球的碰撞模拟
 */
class TheScreenView extends View
{
    private static final String TAG = "Draw";
    private static final String LOG_TAG = "BallCross-Class";
    //界面主线程的控制变量
    private boolean drawing = false;
    //储存当前已有的球的信息
    private ArrayList<Circle> circles;
    private Paint mPaint;
    //两个球的运动范围
    public int WIDTH;
    public int HEIGHT;
    public boolean direct;  //direct = true,表示球从左边出去(serve)， false时，球从右边出去(client)
    public static final double PI = 3.14159265;
    Paint mPaint2 = new Paint();
    private int ballSize = 100;

    //蓝牙通信相关
    /* 一些常量，代表服务器的名称 */
    public static final String PROTOCOL_SCHEME_L2CAP = "btl2cap";
    public static final String PROTOCOL_SCHEME_RFCOMM = "btspp";
    public static final String PROTOCOL_SCHEME_BT_OBEX = "btgoep";
    public static final String PROTOCOL_SCHEME_TCP_OBEX = "tcpobex";

    private BluetoothServerSocket mserverSocket = null;
    private ServerThread startServerThread = null;
    private clientThread clientConnectThread = null;
    private BluetoothSocket socket = null;
    private BluetoothDevice device = null;
    private readThread mreadThread = null;;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    //开启客户端
    private class clientThread extends Thread {
        public void run() {
            while(socket == null) {
                try {
                    //创建一个Socket连接：只需要服务器在注册时的UUID号
                    socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                    //连接
                    //启动接受数据
                    socket.connect();
                    Log.i(LOG_TAG, "connect success");

                    mreadThread = new readThread();
                    mreadThread.start();
                } catch (IOException e) {
                    Log.e("connect", "", e);
                    Log.i(LOG_TAG, "连接服务端异常！断开连接重新试一试。");
                }
            }
        }
    };

    //开启服务器
    private class ServerThread extends Thread {
        public void run() {

            try {
				/* 创建一个蓝牙服务器
				 * 参数分别：服务器名称、UUID	 */
                mserverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(PROTOCOL_SCHEME_RFCOMM,
                        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

                Log.d("server", "wait cilent connect...");

				/* 接受客户端的连接请求 */
                socket = mserverSocket.accept();
                Log.d("server", "accept success !");

                //启动接受数据
                mreadThread = new readThread();
                mreadThread.start();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    };
    /* 停止服务器 */
    private void shutdownServer() {
        new Thread() {
            public void run() {
                if(startServerThread != null)
                {
                    startServerThread.interrupt();
                    startServerThread = null;
                }
                if(mreadThread != null)
                {
                    mreadThread.interrupt();
                    mreadThread = null;
                }
                try {
                    if(socket != null)
                    {
                        socket.close();
                        socket = null;
                    }
                    if (mserverSocket != null)
                    {
                        mserverSocket.close();/* 关闭服务器 */
                        mserverSocket = null;
                    }
                } catch (IOException e) {
                    Log.e("server", "mserverSocket.close()", e);
                }
            };
        }.start();
    }
    /* 停止客户端连接 */
    private void shutdownClient() {
        new Thread() {
            public void run() {
                if(clientConnectThread!=null)
                {
                    clientConnectThread.interrupt();
                    clientConnectThread= null;
                }
                if(mreadThread != null)
                {
                    mreadThread.interrupt();
                    mreadThread = null;
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    socket = null;
                }
            };
        }.start();
    }
    //发送数据
    private void sendMessageHandle(String msg)
    {
        if (socket == null)
        {
            Log.i(LOG_TAG, "没有连接");
            return;
        }
        try {
            OutputStream os = socket.getOutputStream();
            os.write(msg.getBytes());
            Log.i(LOG_TAG, "send data = " + msg.toString());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.i(LOG_TAG, "send fail");
        }
    }

    //读取数据
    private class readThread extends Thread {
        public void run() {

            byte[] buffer = new byte[1024];
            int bytes;
            InputStream mmInStream = null;

            try {
                mmInStream = socket.getInputStream();
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            while (true) {
                try {
                    // Read from the InputStream
                    if( (bytes = mmInStream.read(buffer)) > 0 )
                    {
                        byte[] buf_data = new byte[bytes];
                        for(int i=0; i<bytes; i++)
                        {
                            buf_data[i] = buffer[i];
                        }
                        String s = new String(buf_data);
                        String spStr[] = s.split("/");
                        //接收数据，x坐标，y坐标，angle，以/分割开
                        float x = Float.parseFloat(spStr[0]);
                        if(x > WIDTH - ballSize - ballSize) {
                            x = 0;
                        }else {
                            x = WIDTH;
                        }
                        float y = Float.parseFloat(spStr[1]);
                        double a = Double.parseDouble(spStr[2]);

                        circles.add(new Circle(x, y, ballSize, a, direct));
                        Log.i(LOG_TAG, "receive data, x = " + x + "y = " + y + " a " + a);
                    }
                } catch (IOException e) {
                    try {
                        mmInStream.close();
                    } catch (IOException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    break;
                }
            }
        }
    }


    public TheScreenView(Context context)
    {
        super(context);
        circles = new ArrayList<Circle>();

//        if(MainActivity.isOpen)
//        {
//            Log.i(LOG_TAG, "连接已经打开，可以通信。如果要再建立连接，请先断开！");
//            return;
//        }
        if(MainActivity.serviceOrCilent== MainActivity.ServerOrCilent.CILENT)
        {
            Log.i(LOG_TAG, "It is client!!");
            direct = false;
            circles.add(new Circle(100, 100, ballSize, (new Random().nextFloat())*2*PI, direct));
            String address = MainActivity.BlueToothAddress;
            if(!address.equals("null"))
            {
                device = mBluetoothAdapter.getRemoteDevice(address);
                clientConnectThread = new clientThread();
                clientConnectThread.start();
                MainActivity.isOpen = true;
            }
            else
            {
                Log.i(LOG_TAG, "address is null !");
            }
        }
        else if(MainActivity.serviceOrCilent== MainActivity.ServerOrCilent.SERVICE)
        {
            Log.i(LOG_TAG, "It is service!!");
            direct = true;
            startServerThread = new ServerThread();
            startServerThread.start();
            MainActivity.isOpen = true;
        }

        mPaint = new Paint();
        mPaint.setColor(Color.BLACK);
        mPaint.setAntiAlias(true);
        mPaint2.setStyle(Style.STROKE);
        mPaint2.setColor(Color.WHITE);
        mPaint2.setAntiAlias(true);
        DisplayMetrics dm = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(dm);
        WIDTH  = dm.widthPixels;
        HEIGHT = dm.heightPixels;

        //启动界面线程,开始自动更新界面
        drawing = true;
        new Thread(mRunnable).start();
    }
    private Runnable mRunnable = new Runnable() {
        //界面的主线程
        @Override
        public void run() {
            while( drawing )
            {
                try {
                    //更新球的位置信息
                    update();
                    //通知系统更新界面,相当于调用了onDraw函数
                    postInvalidate();
                    //界面更新的频率,这里是每30ms更新一次界面
                    Thread.sleep(30);
                    //Log.e(TAG, "drawing");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    public void stopDrawing()
    {
        drawing = false;
    }
    @Override
    public void onDraw(Canvas canvas)
    {
        //在canvas上绘上边框
        canvas.drawRect(0, 0, WIDTH, HEIGHT, mPaint2);
        canvas.drawColor(Color.WHITE);
        //在canvas上绘上球
        for( Circle circle : circles)
        {
            canvas.drawCircle(circle.x, circle.y, circle.radius, mPaint);
        }
    }
    //界面的逻辑函数,主要检查球是否发生碰撞,以及更新球的位置
    private void update()
    {
        if( circles.size()>1)
        {
            for( int i1=0; i1<circles.size()-1; i1++)
            {
                //当两个球发生碰撞,交换两个球的角度值
                for( int i2=i1+1; i2<circles.size(); i2++)
                    if( checkBumb(circles.get(i1),circles.get(i2)))
                    {
                        circles.get(i1).changeDerection(circles.get(i2));
                    }
            }
        }
        //更新球的位置
        for( Circle circle: circles)
            circle.updateLocate();
    }
    private boolean checkBumb(Circle c1, Circle c2)
    {
        return (c1.x-c2.x)*(c1.x-c2.x) + (c1.y-c2.y)*(c1.y-c2.y) <= (c1.radius+c2.radius)*(c1.radius+c2.radius);
    }
    /**
     * 自定义的View的内部类,存储每一个球的信息
     */
    class Circle
    {
        float x=250;
        float y=270;
        double angle= (new Random().nextFloat())*2*PI;;
        int speed=15;
        int radius=ballSize;
        boolean direct = false;
        public Circle() {
        }
        public Circle( float x, float y, int r, double angle, boolean direct )
        {
            this.x = x;
            this.y = y;
            radius = r;
            this.angle = angle;
            this.direct = direct;
        }

        public void ballDisappear()
        {
            circles.clear();
        }

        //利用三角函数计算出球的新位置值,当与边界发生碰撞时,改变球的角度
        public void updateLocate()
        {
            x = x+ (float)(speed *Math.cos(angle));
            //Log.v(TAG, Math.cos(angle)+"");
            y = y+ (float)(speed *Math.sin(angle));
            //Log.v(TAG, Math.cos(angle)+"");
            if( x >= WIDTH )
            {
                if(!direct) {    //direct = true,表示球从左边出去(serve)， false时，球从右边出去(client)
                    ballDisappear();
                    sendMessageHandle(x+"/"+y+"/"+angle);
                }else {
                    Log.i(LOG_TAG, "碰到右边界了");
//                Log.i(LOG_TAG, "current coordinates = (" + x + " , " + y + ")");
//                Log.i(LOG_TAG, "cur angle = " + angle);
                    if (angle >= 0 && angle <= (PI / 2))
                        angle = PI - angle;
                    if (angle > 1.5 * PI && angle <= 2 * PI)
                        angle = 3 * PI - angle;
//                Log.iLOG_TAG, "next angle = " + angle);
                }
            }
            if( x <=0 )
            {
                if(direct) {    //direct = true,表示球从左边出去， false时，球从右边出去
                    ballDisappear();
                    sendMessageHandle(x+"/"+y+"/"+angle);
                }else {
                    Log.i(LOG_TAG, "碰到左边界了");
//                Log.i(LOG_TAG, "current coordinates = (" + x + " , " + y + ")");
//                Log.i(LOG_TAG, "cur angle = " + angle);
                    if (angle >= PI && angle <= 1.5 * PI)
                        angle = 3 * PI - angle;
                    if (angle >= PI / 2 && angle < PI)
                        angle = PI - angle;
//                Log.i(LOG_TAG, "next angle = " + angle);
                }
            }
            if( y-radius<=0 || y+radius>=HEIGHT) {
//                Log.i(LOG_TAG, "cur angle = " + angle);
                angle = 2 * PI - angle;
                Log.i(LOG_TAG, "碰到上或下边界了");
//                Log.i(LOG_TAG, "current coordinates = (" + x + " , " + y + ")");
//                Log.i(LOG_TAG, "next angle = " + angle);
            }
        }
        //两球交换角度
        public void changeDerection(Circle other)
        {
            double temp = this.angle;
            this.angle = other.angle;
            other.angle = temp;
        }
    }
}