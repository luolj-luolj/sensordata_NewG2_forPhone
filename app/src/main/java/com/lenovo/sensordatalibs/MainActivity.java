/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lenovo.sensordatalibs;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.USBMonitor;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

/*
 * Simple Java UI to trigger jni function. It is exactly same as Java code
 * in hello-jni.
 */
public class MainActivity extends BaseActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final boolean DEBUG = true;

    // Permission request codes
    private static final int PERMISSIONS_REQUEST_CODE = 2;

    private USBMonitor mUSBMonitor;
    private USBMonitor.UsbControlBlock mCtrlBlock;
    private final Object mSync = new Object();

    private static int mConnected = 0x00;

    private static final int OV_CONNECTED = 0x01;
    private static final int ST_CONNECTED = 0x10;

    private Button mStartButton;
    private Button mStopButton;

    private HandlerThread mWorkThread;
    private WorkHandler mWorkHandler;

    private static final int LIBSENSORDATA_INITIAL = 1;
    private static final int LIBSENSORDATA_RELEASE = 2;
    private static final int LIBSENSORDATA_START = 3;
    private static final int LIBSENSORDATA_STOP = 4;

    private class WorkHandler extends Handler {
        public WorkHandler(Looper looper){
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int r;
            switch (msg.what){
                case LIBSENSORDATA_INITIAL:
                    if (DEBUG) Log.d(TAG, "LIBSENSORDATA_INITIAL");
                    r = nativeInitialSensorData();
                    if (r != 0) {
                        Log.e(TAG, "LIBSENSORDATA_INITIAL failed");
                    } else {
                        mWorkHandler.sendEmptyMessageDelayed(LIBSENSORDATA_START, 1000);
                    }
                    break;
                case LIBSENSORDATA_RELEASE:
                    if (DEBUG) Log.d(TAG, "LIBSENSORDATA_RELEASE");
                    r = nativeReleaseSensorData();
                    if (r != 0) {
                        Log.e(TAG, "LIBSENSORDATA_RELEASE failed");
                    }
                    break;
                case LIBSENSORDATA_START:
                    if (DEBUG) Log.d(TAG, "LIBSENSORDATA_START mConnected = " + mConnected);
                    r = nativeStartSensorData("imu");
                    r = nativeStartSensorData("camera:fisheye");
                    if (r != 0) {
                        Log.e(TAG, "LIBSENSORDATA_START failed");
                    } else {
                        if (DEBUG) Log.d(TAG, "START successfully");
                    }
                    break;
                case LIBSENSORDATA_STOP:
                    if (DEBUG) Log.d(TAG, "LIBSENSORDATA_STOP mConnected = " + mConnected);
                    r = nativeStopSensorData("camera:fisheye");
                    r = nativeStopSensorData("imu");
                    if (r != 0) {
                        Log.e(TAG, "LIBSENSORDATA_STOP failed");
                    } else {
                        mWorkHandler.sendEmptyMessageDelayed(LIBSENSORDATA_RELEASE, 1000);
                    }
                    break;
                default:
                    break;
            }
        }
    }


    private USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            int vid = device.getVendorId();
            int pid = device.getProductId();
            if (DEBUG) Log.v(TAG, String.format("=== onAttach: VID=0x%04x, PID=0x%04x, mConnected=0x%04x\n",vid, pid, mConnected));
            if (((vid == 0x05A9) && (pid == 0x0F87) && ((mConnected & OV_CONNECTED)!=OV_CONNECTED))
                    || ((vid == 0x0483) && (pid == 0x7705) && ((mConnected&ST_CONNECTED)!= ST_CONNECTED))) {
                mUSBMonitor.requestPermission(device);
            }
        }

        @Override
        public void onDettach(UsbDevice device) {
            mConnected = 0x00;
            //Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
            if (DEBUG) {
                Log.v(TAG,
                        String.format("=== onDettach: VID=0x%04x, PID=0x%04x\n", device.getVendorId(),
                                device.getProductId()));
            }
        }

        String getUSBFSName(final USBMonitor.UsbControlBlock ctrlBlock) {
            String DEFAULT_USBFS = "/dev/bus/usb";
            String result = null;
            final String name = ctrlBlock.getDeviceName();
            final String[] v = !TextUtils.isEmpty(name) ? name.split("/") : null;
            if ((v != null) && (v.length > 2)) {
                final StringBuilder sb = new StringBuilder(v[0]);
                for (int i = 1; i < v.length - 2; i++) {
                    sb.append("/").append(v[i]);
                }
                result = sb.toString();
            }
            if (TextUtils.isEmpty(result)) {
                Log.w(TAG, "failed to get USBFS path, try to use default path:" + name);
                result = DEFAULT_USBFS;
            }
            return result;
        }

        @Override
        public void onConnect(UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock,
                boolean createNew) {
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    synchronized (mSync) {
                        try {
                            USBMonitor.UsbControlBlock mCtrlBlock = ctrlBlock.clone();
                            int devId = mCtrlBlock.getDeviceId();
                            int vid = mCtrlBlock.getVenderId();
                            int pid = mCtrlBlock.getProductId();
                            if (DEBUG) {
                                Log.d(TAG,
                                        String.format("onConnect: Device ID = %d\nVID=0x%04x\nPID=0x%04x\n", devId,
                                                vid, pid));
                            }
                            if ((vid == 0x05A9) && (pid == 0x0F87)) {
                                if (DEBUG) Log.d(TAG, "IMU MATCH FOUND!");
                                String usbfs_path = mCtrlBlock.getDeviceName();
                                if (DEBUG) Log.d(TAG, "imu_usbfs_path = " + usbfs_path);
                                int file_descriptor = mCtrlBlock.getFileDescriptor();
                                if (DEBUG) Log.d(TAG, "imu_fd = " + file_descriptor);
                                String usb_fs = getUSBFSName(mCtrlBlock);
                                if (DEBUG) Log.d(TAG, "imu_usb_fs = " + usb_fs);
                                nativeSetUsbFileDescriptor(mCtrlBlock.getVenderId(), mCtrlBlock.getProductId(),
                                        mCtrlBlock.getFileDescriptor(),
                                        mCtrlBlock.getBusNum(),
                                        mCtrlBlock.getDevNum(),
                                        getUSBFSName(mCtrlBlock));
                                showToast(R.string.ov_connected);
                                mConnected = (mConnected | OV_CONNECTED);
                            }
                            if ((vid == 0x0483) && (pid == 0x7705)) {
                                if (DEBUG) Log.d(TAG, "ST MATCH FOUND!");
                                String usbfs_path = mCtrlBlock.getDeviceName();
                                if (DEBUG) Log.d(TAG, "st_usbfs_path = " + usbfs_path);
                                int file_descriptor = mCtrlBlock.getFileDescriptor();
                                if (DEBUG) Log.d(TAG, "st_fd = " + file_descriptor);
                                String usb_fs = getUSBFSName(mCtrlBlock);
                                if (DEBUG) Log.d(TAG, "st_usb_fs = " + usb_fs);
                                nativeSetStUfd(mCtrlBlock.getVenderId(), ctrlBlock.getProductId(),
                                        mCtrlBlock.getFileDescriptor(),
                                        mCtrlBlock.getBusNum(),
                                        mCtrlBlock.getDevNum(),
                                        getUSBFSName(mCtrlBlock));
                                showToast(R.string.st_connected);
                                mConnected = (mConnected | ST_CONNECTED);
                            }
                        } catch (IllegalStateException ex) {
                            if (DEBUG) Log.d(TAG, "ex:", ex);
                        } catch (CloneNotSupportedException e) {
                            if (DEBUG) Log.d(TAG, "ex:", e);
                        }
                    }
                }
            }, 0);

            if (mConnected == 0x11) {
                mStartButton.setVisibility(View.VISIBLE);
            }
        }


        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            try {
                USBMonitor.UsbControlBlock mCtrlBlock = ctrlBlock.clone();
                int devId = mCtrlBlock.getDeviceId();
                int vid = mCtrlBlock.getVenderId();
                int pid = mCtrlBlock.getProductId();
                if (DEBUG) {
                    Log.d(TAG, String.format(
                            "onDisconnect: Device ID = %d\\nVID=0x%04x\\nPID=0x%04x\\n\", "
                                    + "devId, vid, pid"));
                }
            } catch (IllegalStateException ex) {
                if (DEBUG) Log.d(TAG, "ex:", ex);
            } catch (CloneNotSupportedException e) {
                if (DEBUG) Log.d(TAG, "ex:", e);
            }
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    synchronized (mSync) {// TODO:
                    }
                }
            }, 0);
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.d(TAG, "onCancel");
        }
    };

    /**
     * Checks for activity permissions.
     *
     * @return whether the permissions are already granted.
     */
    private boolean arePermissionsEnabled() {
        boolean cameraPermission =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED;
        boolean writePermission =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
        return (cameraPermission && writePermission);
    }

    /** Handles the requests for activity permissions. */
    private void requestPermissions() {
        final String[] permissions =
                new String[] {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE);
    }

    /** Callback for the result from requesting permissions. */
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!arePermissionsEnabled()) {
            Toast.makeText(this, R.string.no_permissions, Toast.LENGTH_LONG).show();
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    || !ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.CAMERA)) {
                // Permission denied with checking "Do not ask again".
                launchPermissionsSettings();
            }
            finish();
        }
    }

    private void launchPermissionsSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView tv = findViewById(R.id.text_view);
        tv.setText( stringFromJNI() );

        mStartButton = findViewById(R.id.start_test);
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //start test
                mWorkHandler.sendEmptyMessage(LIBSENSORDATA_INITIAL);
            }
        });

        mStopButton = findViewById(R.id.stop_test);
        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //start test
                mWorkHandler.sendEmptyMessage(LIBSENSORDATA_STOP);
            }
        });
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

        mWorkThread = new HandlerThread("WorkThread");
        mWorkThread.start();
        mWorkHandler = new WorkHandler(mWorkThread.getLooper());
        if (DEBUG) Log.d(TAG, "mWorkThread name = " + mWorkThread.getName() + ", id = " + mWorkThread.getId());
    }

    static {
        System.loadLibrary("sensordata-libs");
    }

    public native String  stringFromJNI();
    private native void nativeSetUsbFileDescriptor(int vid, int pid, int fd, int busnum, int devaddr, String usbfs_str);
    private native void nativeSetStUfd(int vid, int pid, int fd, int busnum, int devaddr, String usbfs_str);
    private native int nativeInitialSensorData();
    private native int nativeReleaseSensorData();
    private native int nativeStartSensorData(String who);
    private native int nativeStopSensorData(String who);

    //override AppCompatActivity, just print message to check lifecycle.
    @Override
    protected void onPostResume() {
        if (DEBUG)Log.d(TAG, "onPostResume");
        super.onPostResume();
    }

    @Override
    protected void onStart() {
        if (DEBUG)Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        if (DEBUG)Log.d(TAG, "onStop");
        super.onStop();
    }

    //Override FragmentActivity, just print message to check lifecycle.
    @Override
    protected void onResume() {
        if (DEBUG)Log.d(TAG, "onResume");
        super.onResume();
        synchronized (mSync) {
            if (mUSBMonitor != null) {
                mUSBMonitor.register();
            }
        }
        // Checks for activity permissions, if not granted, requests them.
        if (!arePermissionsEnabled()) {
            requestPermissions();
            return;
        }
    }

    @Override
    protected void onResumeFragments() {
        if (DEBUG)Log.d(TAG, "onResumeFragments");
        super.onResumeFragments();
    }

    //Override BaseActivity
    @Override
    protected void onPause() {
        if (DEBUG)Log.d(TAG, "onPause");
        super.onPause();
        synchronized (mSync) {
            if (mUSBMonitor != null) {
                mUSBMonitor.unregister();
            }
        }
    }

    @Override
    protected synchronized void onDestroy() {
        if (DEBUG)Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (mWorkThread != null) {
            mWorkThread.quit();
            mWorkThread = null;
        }
    }
}
