package com.test.helloeeg;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.neurosky.thinkgear.*;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class HelloEEGActivity extends Activity {

    private static final String TAG = "HelloEEG";

    BluetoothAdapter bluetoothAdapter;
    static TGDevice tgDevice;
    int subjectContactQuality_last;
    int subjectContactQuality_cnt;

    final boolean rawEnabled = true;

    double task_famil_baseline, task_famil_cur, task_famil_change;
    boolean task_famil_first;
    double task_diff_baseline, task_diff_cur, task_diff_change;
    boolean task_diff_first;

    ScrollView sv;
    TextView tv;
    Button bt_connect;
    Button bt_call;
    Button bt_save;

    // 绘图
    private SurfaceView showSurfaceView;
    private DrawWave mDrawWave;

    //时间
    Calendar calendar = Calendar.getInstance();
    Time time ;

    ArrayList<Integer> rawdata_list = new ArrayList<Integer>();
    short motion = 0;

    private Random random = new Random();


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        time = new Time();
        time.setToNow();

        sv = (ScrollView) findViewById(R.id.scrollView1);
        tv = (TextView) findViewById(R.id.textView1);
        tv.setText("");
        tv.append("Android version: " + Integer.valueOf(android.os.Build.VERSION.SDK) + "\n");

        subjectContactQuality_last = -1; /* start with impossible value */
        subjectContactQuality_cnt = 200; /* start over the limit, so it gets reported the 1st time */


        // Check if Bluetooth is available on the Android device
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {

            // Alert user that Bluetooth is not available
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_LONG).show();
            //finish();
            return;

        } else {

            // create the TGDevice
            tgDevice = new TGDevice(bluetoothAdapter, handler);
        }

        tv.append("NeuroSky: " + TGDevice.version + " " + TGDevice.build_title);
        tv.append("\n");

        task_famil_baseline = task_famil_cur = task_famil_change = 0.0;
        task_famil_first = true;
        task_diff_baseline = task_diff_cur = task_diff_change = 0.0;
        task_diff_first = true;

        //showSurfaceView = (SurfaceView) findViewById(R.id.showSurfaceView);
        //mDrawWave = new DrawWave(this,showSurfaceView);

        bt_connect = (Button) findViewById(R.id.bt_connect);
        bt_connect.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                // mDrawWave.Refresh();
                // TODO Auto-generated method stub
                tgDevice.connect(true);
                //mDrawWave.startDraw();
            }

        });

        bt_call = (Button) findViewById(R.id.button_call);
        bt_call.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                // TODO Auto-generated method stub
                Call("13121273120");
            }

        });

        bt_save = (Button) findViewById(R.id.bt_save);
        bt_save.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                // TODO Auto-generated method stub
                CreateFileNameDialog();
            }

        });


    }
    /* end onCreate() */

    //turn off app when touch return button of phone
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        //if (!bluetoothAdapter.isEnabled()) {
        //  Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        //startActivityForResult(enableIntent, 1);
        //}
    }

    @Override
    public void onPause() {
        // tgDevice.close();
        super.onPause();
    }

    @Override
    public void onStop() {
        saveFile("END-RAWDATA",rawdata_list);
        rawdata_list.clear();
        //tgDevice.close();
        super.onStop();

    }

    @Override
    public void onDestroy() {
        //tgDevice.close();
        super.onDestroy();
    }

    /**
     * Handles messages from TGDevice
     */
    Timer timer = new Timer();
    TimerTask task = null;

    class DataThread extends Thread {

        public void run() {
            if (task != null) {
                task.cancel();
            }
            task = new TimerTask() {
                @Override
                public void run() {
                    Message msg = new Message();
                    msg.what = TGDevice.MSG_RAW_DATA;
                    msg.arg1 = 1024;
                    handler.sendMessage(msg);

                }
            };
            timer.schedule(task, 0, 2);

        }
    }

    public void startData(View v){
        new DataThread().start();
        Log.e("Data","start");
    }

    int count = 0;
    int save_count = 0;
    private final Handler handler = new MyHandler(this);

    static class MyHandler extends Handler{
        WeakReference<HelloEEGActivity> mactivity;

        public MyHandler(HelloEEGActivity activity){
            mactivity = new WeakReference<HelloEEGActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            final HelloEEGActivity mInstance = mactivity.get();
            TextView tv = mInstance.tv;
            TGDevice tgDevice = mInstance.tgDevice;
            ScrollView sv = mInstance.sv;

            switch (msg.what) {
                    case TGDevice.MSG_MODEL_IDENTIFIED:
                        /*
                         * now there is something connected,
                         * time to set the configurations we need
                         */
                        tv.append("Model Identified\n");
                        tgDevice.setBlinkDetectionEnabled(true);
                        tgDevice.setTaskDifficultyRunContinuous(true);
                        tgDevice.setTaskDifficultyEnable(true);
                        tgDevice.setTaskFamiliarityRunContinuous(true);
                        tgDevice.setTaskFamiliarityEnable(true);
                        tgDevice.setRespirationRateEnable(true); /// not allowed on EEG hardware, here to show the override message
                        break;

                    case TGDevice.MSG_STATE_CHANGE:

                        switch (msg.arg1) {
                            case TGDevice.STATE_IDLE:
                                break;
                            case TGDevice.STATE_CONNECTING:
                                tv.append("Connecting...\n");
                                break;
                            case TGDevice.STATE_CONNECTED:
                                tv.append("Connected.\n");
                                tgDevice.start();
                                break;
                            case TGDevice.STATE_NOT_FOUND:
                                tv.append("Could not connect to any of the paired BT devices.  Turn them on and try again.\n");
                                tv.append("Bluetooth devices must be paired 1st\n");
                                break;
                            case TGDevice.STATE_ERR_NO_DEVICE:
                                tv.append("No Bluetooth devices paired.  Pair your device and try again.\n");
                                break;
                            case TGDevice.STATE_ERR_BT_OFF:
                                tv.append("Bluetooth is off.  Turn on Bluetooth and try again.");
                                break;

                            case TGDevice.STATE_DISCONNECTED:
                                tv.append("Disconnected.\n");
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mInstance.DeviceConnect();
                                    }
                                }).start();
                        } /* end switch on msg.arg1 */

                        break;

                    case TGDevice.MSG_POOR_SIGNAL:
                        /* display signal quality when there is a change of state, or every 30 reports (seconds) */
                        if (mInstance.subjectContactQuality_cnt >= 30 || msg.arg1 != mInstance.subjectContactQuality_last) {
                            if (msg.arg1 == 0) tv.append("SignalQuality: is Good: " + msg.arg1 + "\n");
                            else tv.append("SignalQuality: is POOR: " + msg.arg1 + "\n");

                            mInstance.subjectContactQuality_cnt = 0;
                            mInstance.subjectContactQuality_last = msg.arg1;
                        } else mInstance.subjectContactQuality_cnt++;
                        break;

                    //RAW_DATA    !!!!!!!!
                    case TGDevice.MSG_RAW_DATA:
                        mInstance.count += 1;
                        // mInstance.mDrawWave.in = -(double) (msg.arg1)/2048;
                        mInstance.rawdata_list.add((msg.arg1<<3)+mInstance.motion);

                        if(mInstance.rawdata_list.size()%1000==0)
                            Log.e("rawdata",mInstance.rawdata_list.size()+":"+msg.arg1);

                        if (mInstance.motion != 0) {
                            mInstance.motion = 0;
                        }

                        if (mInstance.rawdata_list.size()>=150000){
                            mInstance.calendar = Calendar.getInstance();
                            if (mInstance.count % 150000 == 0 ) {
                                final String filename = String.format(mInstance.getResources().getString(R.string.auto_file_name),
                                        mInstance.calendar.get(Calendar.DAY_OF_YEAR), mInstance.calendar.get(Calendar.HOUR_OF_DAY),
                                        mInstance.calendar.get(Calendar.MINUTE),
                                        mInstance.calendar.get(Calendar.SECOND),
                                        mInstance.rawdata_list.size());
                            }
                            mInstance.saveFile("AUTO-RAWDATA",(ArrayList<Integer>) mInstance.rawdata_list.clone());
                            Toast.makeText(mInstance, "正在自动保存...", Toast.LENGTH_LONG).show();
                            mInstance.rawdata_list.clear();
                        }
                        /* Handle raw EEG/EKG data here */
                        break;


                    case TGDevice.MSG_ATTENTION:
                        tv.append("Attention: " + msg.arg1 + "\n");
                        break;

                    case TGDevice.MSG_MEDITATION:
                        tv.append("Meditation: " + msg.arg1 + "\n");
                        break;

                    case TGDevice.MSG_EEG_POWER:

                        TGEegPower e = (TGEegPower) msg.obj;
                        tv.append("delta: " + e.delta + " theta: " + e.theta + " alpha1: " + e.lowAlpha + " alpha2: " + e.highAlpha + "\n");
                        break;

                    case TGDevice.MSG_FAMILIARITY:
                        mInstance.task_famil_cur = (Double) msg.obj;
                        if (mInstance.task_famil_first) {
                            mInstance.task_famil_first = false;
                        } else {
                            /*
                             * calculate the percentage change from the previous sample
                             */
                            mInstance.task_famil_change = mInstance.calcPercentChange(mInstance.task_famil_baseline, mInstance.task_famil_cur);
                            if (mInstance.task_famil_change > 500.0 || mInstance.task_famil_change < -500.0) {
                                tv.append("     Familiarity: excessive range\n");
                                //Log.i( "familiarity: ", "excessive range" );
                            } else {
                                tv.append("     Familiarity: " + mInstance.task_famil_change + " %\n");
                                //Log.i( "familiarity: ", String.valueOf( task_famil_change ) + "%" );
                            }
                        }
                        mInstance.task_famil_baseline = mInstance.task_famil_cur;
                        break;

                    case TGDevice.MSG_DIFFICULTY:
                        mInstance.task_diff_cur = (Double) msg.obj;
                        if (mInstance.task_diff_first) {
                            mInstance.task_diff_first = false;
                        } else {
                            /*
                             * calculate the percentage change from the previous sample
                             */
                            mInstance.task_diff_change = mInstance.calcPercentChange(mInstance.task_diff_baseline, mInstance.task_diff_cur);
                            if (mInstance.task_diff_change > 500.0 || mInstance.task_diff_change < -500.0) {
                                tv.append("     Difficulty: excessive range %\n");
                                //Log.i("difficulty: ", "excessive range" );
                            } else {
                                tv.append("     Difficulty: " + mInstance.task_diff_change + " %\n");
                                //Log.i( "difficulty: ", String.valueOf( task_diff_change ) + "%" );
                            }
                        }
                        mInstance.task_diff_baseline = mInstance.task_diff_cur;
                        break;

                    case TGDevice.MSG_ZONE:
                        switch (msg.arg1) {
                            case 3:
                                tv.append("          Zone: Elite\n");
                                break;
                            case 2:
                                tv.append("          Zone: Intermediate\n");
                                break;
                            case 1:
                                tv.append("          Zone: Beginner\n");
                                break;
                            default:
                            case 0:
                                tv.append("          Zone: relax and try to focus\n");
                                break;
                        }
                        break;

                    case TGDevice.MSG_BLINK:
                        tv.append("Blink: " + msg.arg1 + "\n");
                        break;

                    case TGDevice.MSG_ERR_CFG_OVERRIDE:
                        switch (msg.arg1) {
                            case TGDevice.ERR_MSG_BLINK_DETECT:
                                tv.append("Override: blinkDetect" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "Override: blinkDetect", Toast.LENGTH_SHORT).show();
                                break;
                            case TGDevice.ERR_MSG_TASKFAMILIARITY:
                                tv.append("Override: Familiarity" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "Override: Familiarity", Toast.LENGTH_SHORT).show();
                                break;
                            case TGDevice.ERR_MSG_TASKDIFFICULTY:
                                tv.append("Override: Difficulty" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "Override: Difficulty", Toast.LENGTH_SHORT).show();
                                break;
                            case TGDevice.ERR_MSG_POSITIVITY:
                                tv.append("Override: Positivity" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "Override: Positivity", Toast.LENGTH_SHORT).show();
                                break;
                            case TGDevice.ERR_MSG_RESPIRATIONRATE:
                                tv.append("Override: Resp Rate" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "Override: Resp Rate", Toast.LENGTH_SHORT).show();
                                break;
                            default:
                                tv.append("Override: code: " + msg.arg1 + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "Override: code: " + msg.arg1 + "", Toast.LENGTH_SHORT).show();
                                break;
                        }
                        break;
                    case TGDevice.MSG_ERR_NOT_PROVISIONED:
                        switch (msg.arg1) {
                            case TGDevice.ERR_MSG_BLINK_DETECT:
                                tv.append("No Support: blinkDetect" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "No Support: blinkDetect", Toast.LENGTH_SHORT).show();
                                break;
                            case TGDevice.ERR_MSG_TASKFAMILIARITY:
                                tv.append("No Support: Familiarity" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "No Support: Familiarity", Toast.LENGTH_SHORT).show();
                                break;
                            case TGDevice.ERR_MSG_TASKDIFFICULTY:
                                tv.append("No Support: Difficulty" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "No Support: Difficulty", Toast.LENGTH_SHORT).show();
                                break;
                            case TGDevice.ERR_MSG_POSITIVITY:
                                tv.append("No Support: Positivity" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "No Support: Positivity", Toast.LENGTH_SHORT).show();
                                break;
                            case TGDevice.ERR_MSG_RESPIRATIONRATE:
                                tv.append("No Support: Resp Rate" + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "No Support: Resp Rate", Toast.LENGTH_SHORT).show();
                                break;
                            default:
                                tv.append("No Support: code: " + msg.arg1 + "\n");
                                Toast.makeText(mInstance.getApplicationContext(), "No Support: code: " + msg.arg1 + "", Toast.LENGTH_SHORT).show();
                                break;
                        }
                        break;
                    default:
                        break;

                } /* end switch on msg.what */

                sv.fullScroll(View.FOCUS_DOWN);

            } /* end handleMessage() */
    }

    public double calcPercentChange(double baseline, double current) {
        double change;

        if (baseline == 0.0) baseline = 1.0; //don't allow divide by zero
        /*
         * calculate the percentage change
         */
        change = current - baseline;
        change = (change / baseline) * 1000.0 + 0.5;
        change = Math.floor(change) / 10.0;
        return (change);
    }

    /**
     * This method is called when the user clicks on the "Connect" button.
     *
     * @param view
     */
    public void doStuff(View view) {
        DeviceConnect();

    } /* end doStuff() */

    public void DeviceConnect(){
        if (tgDevice.getState() != TGDevice.STATE_CONNECTING && tgDevice.getState() != TGDevice.STATE_CONNECTED) {
            tgDevice.connect(rawEnabled);
        }
    }

    public void ChangeMotion(View view) {
        switch (view.getId()) {
            case R.id.bt_A:
                motion = 1;
                Toast.makeText(this, "A", Toast.LENGTH_SHORT).show();
                break;
            case R.id.bt_B:
                motion = 2;
                Toast.makeText(this, "B", Toast.LENGTH_SHORT).show();
                break;
            case R.id.bt_C:
                motion =  3 ;
                Toast.makeText(this, "C", Toast.LENGTH_SHORT).show();
                break;
            case R.id.bt_D:
                motion =  4 ;
                Toast.makeText(this, "D", Toast.LENGTH_SHORT).show();
                break;
            case R.id.bt_E:
                motion =  5 ;
                Toast.makeText(this, "E", Toast.LENGTH_SHORT).show();
                break;
            case R.id.bt_F:
                motion =  6 ;
                Toast.makeText(this, "F", Toast.LENGTH_SHORT).show();
                break;
            case R.id.bt_G:
                motion =  7 ;
                Toast.makeText(this, "G", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    public void Call(String phone) {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
        startActivity(intent);
    }

    public void saveFile(String fileName_before, ArrayList<Integer> arrlist) {
        // 获取文件名
//        calendar = Calendar.getInstance();
//        String fileName_time = String.format(getResources().getString(R.string.filename),
//                calendar.get(Calendar.DAY_OF_YEAR), calendar.get(Calendar.HOUR_OF_DAY),
//                calendar.get(Calendar.MINUTE),
//                calendar.get(Calendar.SECOND),
//                save_count,
//                arrlist.size());

        String filename;

        if(fileName_before.equals("AUTO-RAWDATA") || fileName_before.equals("END-RAWDATA"))
        {
            time.setToNow();
            calendar = Calendar.getInstance();
            String fileName_time = String.format(getResources().getString(R.string.filename),
                    calendar.get(Calendar.DAY_OF_YEAR), calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    calendar.get(Calendar.SECOND),
                    save_count,
                    arrlist.size());
//            String fileName_time = String.format(getResources().getString(R.string.filename),
//                    time.yearDay+1, time.hour,
//                    time.minute,
//                    time.second,
//                    save_count,
//                    arrlist.size());

            filename = fileName_before+fileName_time;
        }
        else
            filename = fileName_before;
        // 创建String对象保存文件名路径
        try {
            // 创建指定路径的文件
            File file = new File(Environment.getExternalStorageDirectory(), filename);
            // 如果文件不存在
            if (file.exists()) {
                // 创建新的空文件
                file.delete();
            }
            file.createNewFile();
            // 获取文件的输出流对象
            DataOutputStream os = new DataOutputStream(new FileOutputStream(file, true));

            for(int i = 0;i<arrlist.size();i++) {
                os.writeShort(arrlist.get(i));
            }
            // 最后关闭文件输出流
            os.close();
            Log.e("save",filename);
            save_count+=1;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void CreateButtonDialog(String str) {
        new AlertDialog.Builder(this).setTitle(str)
                .setPositiveButton("确定",null)
                .show();

    }

    public AlertDialog CreateDialog(String str) {
        return new AlertDialog.Builder(this).setTitle(str)
                .show();
    }

    public void CreateFileNameDialog() {
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(20, 20, 30, 10);
        final EditText et1 = new EditText(this);
        et1.setLayoutParams(lp);
        et1.setHint("文件名");
        calendar = Calendar.getInstance();
        et1.setText(String.format(getResources().getString(R.string.file_name2),
                calendar.get(Calendar.DAY_OF_YEAR), calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND),
                save_count,
                rawdata_list.size()));

        layout.addView(et1);
        new AlertDialog.Builder(this).setTitle("设置文件名")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setView(layout)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String input1 = et1.getText().toString();
                        if (input1.equals("")) {
                            Toast.makeText(getApplicationContext(), "不能为空" + input1, Toast.LENGTH_SHORT).show();
                        } else {
                            AlertDialog  new_dialog = new AlertDialog.Builder(HelloEEGActivity.this).setTitle("正在保存...").show();
                            saveFile(input1, (ArrayList<Integer>) rawdata_list.clone());
                            rawdata_list.clear();
                            new_dialog.dismiss();
                            CreateDialog("保存完毕");
                        }
                    }
                    })
                .setNegativeButton("取消",null)
                .show();
                }

} /* end HelloEEGActivity() */
