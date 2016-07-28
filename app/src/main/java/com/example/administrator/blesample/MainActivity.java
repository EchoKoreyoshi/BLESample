package com.example.administrator.blesample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private Handler mHandler = new Handler();
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    // 10秒后停止查找搜索.
    private static final long SCAN_PERIOD = 10000;
    private BluetoothDevice mDevice;
    private TextView mSystol;
    private TextView mDiastol;
    private TextView mPulse;
    private TextView mState;
    private boolean isConnect;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSystol = (TextView) findViewById(R.id.systol);
        mDiastol = (TextView) findViewById(R.id.diastol);
        mPulse = (TextView) findViewById(R.id.pulse);
        mState = (TextView) findViewById(R.id.state);

        // 检查当前手机是否支持ble 蓝牙,如果不支持退出程序
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "设备不支持蓝牙4.0", Toast.LENGTH_SHORT).show();
            mState.setText("设备不支持蓝牙4.0");
            finish();
        }

        // 初始化 Bluetooth adapter, 通过蓝牙管理器得到一个参考蓝牙适配器(API必须在以上android4.3或以上和版本)
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // 检查设备上是否支持蓝牙
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "不支持蓝牙", Toast.LENGTH_SHORT).show();
            mState.setText("不支持蓝牙");
            finish();
        }
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, final byte[] scanRecord) {
            if ("Technaxx BP".equalsIgnoreCase(device.getName()) && mDevice == null) {
                // mState.setText("搜索到设备:" + device.getName());
                mDevice = device;
                connectBlueTooth();
                if (mScanning) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    mScanning = false;
                }
                Log.d("MainActivity", "搜索到设备:" + device.getName());
                Toast.makeText(MainActivity.this, "搜索到设备:" + device.getName(), Toast.LENGTH_SHORT).show();
            }
        }
    };

    //链接蓝牙
    private void connectBlueTooth() {
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDevice.getAddress());
        }
        isConnect = true;
        // mState.setText("开始链接设备：" + mDevice.getName());
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    // Code to manage Service lifecycle.
    //管理服务生命周期
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.d("MainActivity", "无法初始化蓝牙");
                finish();
            }
            //自动连接到设备成功启动初始化
            mBluetoothLeService.connect(mDevice.getAddress());


        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    private boolean mConnected;
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server. 连接一个GATT服务
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server. 断开GATT服务
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services. 发现GATTA服务
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations. 接收来自服务的数据，这个result可以读或者通知操作
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                Log.d("MainActivity", "蓝牙已链接");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                Log.d("MainActivity", "蓝牙断开链接");
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //在user接口显示所有支持的服务和特征
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    //"00002af0-0000-1000-8000-00805f9b34fb"
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
            return;
        String uuid = null;
       /* String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);*/
        //  ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<>();
        mGattCharacteristics = new ArrayList<>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            //   HashMap<String, String> currentServiceData = new HashMap<>();
            uuid = gattService.getUuid().toString();
         /*   currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);*/

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                //找到特征的uuid
                if ("00002af0-0000-1000-8000-00805f9b34fb".equalsIgnoreCase(uuid)) {
                    connectCharacter(gattCharacteristic);
                }
                /*currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));*/
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
    }

    private void connectCharacter(BluetoothGattCharacteristic gattCharacteristic) {
        if (mGattCharacteristics != null) {
            final int charaProp = gattCharacteristic.getProperties();
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {

                if (mNotifyCharacteristic != null) {
                    mBluetoothLeService.setCharacteristicNotification(
                            mNotifyCharacteristic, false);
                    mNotifyCharacteristic = null;
                }
                mBluetoothLeService.readCharacteristic(gattCharacteristic);
            }
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                mNotifyCharacteristic = gattCharacteristic;
                mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
            }
        }
    }

    private void displayData(String data) {
//        mState.setText("链接成功，开始接受数据。");
        if (data != null) {
            //筛选数据: 02 40 DD 0C 1C 00 66 00 33 00 00 00 33 00 00 00 EB
            if (data.length() > 50 && data.contains("02")) {
                //截取数据
                String s = data.substring(data.length() - 49, data.length() - 1);
                //分离数据
                String[] strings = s.split(" ");
                if (strings[4].equals("1C") && checked(strings)) {
                    //测量成功
                    //高压
                    Integer systol = Integer.valueOf(strings[5] + strings[6], 16);
                    //低压
                    Integer diastol = Integer.valueOf(strings[7] + strings[8], 16);
                    //心率
                    Integer pulse = Integer.valueOf(strings[11] + strings[12], 16);
                    //显示状态
                    displayState(systol, diastol, pulse);


                } else if (strings[4].equals("3C") && checked(strings)) {
                    //测量失败
                    Integer error = Integer.valueOf(strings[11] + strings[12], 16);

                    //显示错误-->显示在TextView上
                    displayError(error);
                    //mState.setText("测量失败");

                }
            }

        }
        Log.d("接收到的数据：", data);

    }

    /**
     * 显示状态
     *
     * @param systol  高压
     * @param diastol 低压
     * @param pulse   心律
     */
    private void displayState(Integer systol, Integer diastol, Integer pulse) {

        mSystol.setText("  " + systol + Constant.BLOOD_PRESSURE_UNIT);
        mDiastol.setText("  " + diastol + Constant.BLOOD_PRESSURE_UNIT);
        mPulse.setText("  " + pulse + Constant.HEART_RATE);

        if (systol >= 90 && systol <= 140 && diastol >= 60 && diastol <= 90) {
            mState.setText(" 您的血压正常。");
        } else if (systol < 90 || diastol < 60) {
            mState.setText(" 您的血压偏低。");
        } else if (systol > 140 || diastol > 90) {
            mState.setText(" 您的血压偏高。");

        }
        Log.d(TAG, systol + "------" + diastol + "-------" + pulse);
    }

    /**
     * 显示错误
     *
     * @param error
     */
    private void displayError(Integer error) {
        switch (error) {
            case 01:
                mState.setText("  测量失败，传感器信号异常。");
                break;
            case 02:
                mState.setText("  测量失败，测量不出结果。");
                break;
            case 03:
                mState.setText("  测量失败，测量结果异常。");
                break;
            case 04:
                mState.setText("  测量失败，腕带过松或漏气。");
                break;
            case 05:
                mState.setText("  测量失败，腕带过紧或气路堵塞。");
                break;
            case 06:
                mState.setText("  测量失败，测量中压力干扰严重。");
                break;
            case 07:
                mState.setText("  测量失败，压力超过300。");
                break;
        }
    }

    /**
     * 利用数据校验位 异或校验数据
     * byte17 = byte2 ^ byte3 ^ byte4 ^ ……… ^ byte16
     *
     * @param strings
     * @return
     */
    private boolean checked(String[] strings) {
        //校验位
        Integer check = Integer.valueOf(strings[strings.length - 1], 16);
        int temp = Integer.valueOf(strings[1], 16);
        for (int i = 2; i < strings.length - 1; i++) {
            temp ^= Integer.valueOf(strings[i], 16);
        }
        return check == temp;
    }


    private static final int REQUEST_ENABLE_BT = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 如果用户没有选择有效的蓝牙
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        if (isConnect)
            unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 为了确保设备上蓝牙能使用, 如果当前蓝牙设备没启用,弹出对话框向用户要求授予权限来启用
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        scanLeDevice(true);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
