package com.example.robotcontroller;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    Button btn_up, btn_down, btn_left, btn_right;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;

    public int direction = 0;

    private static final String TAG = "MainActivity";

    UUID SERVICE_UUID = UUID.fromString("6f4cee11-d76c-49a5-b2d5-5b91e1db87b2");
    UUID CHARACTERISTIC_DIRECTION_UUID = UUID.fromString("7dbfd27a-b283-4ea3-a90d-75c58aea3511");
    UUID DESCRIPTOR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Set<BluetoothDevice> devices = new HashSet<>();

    private BluetoothGattServer gattServer;
    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settings) {
            Log.i(TAG, "Advertise Start Success");
        }

        @Override
        public void onStartFailure(int error) {
            Log.w(TAG, "uh oh" + error);
        }

    };
    private BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
                // Remove device from list to receive notifications
                devices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_DIRECTION_UUID.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read direction");
                byte[] value = toByteArray(direction);
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            if (DESCRIPTOR_CONFIG_UUID.equals(descriptor.getUuid())) {
                Log.d(TAG, "Config descriptor read request");
                byte[] returnValue;
                if (devices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, returnValue);
            } else {
                Log.w(TAG, "Unknown descriptor read request");
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (DESCRIPTOR_CONFIG_UUID.equals(descriptor.getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to notifications: " + device);
                    devices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Unsubscribe device from notifications: " + device);
                    devices.remove(device);
                }

                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                }
            } else {
                Log.w(TAG, "Unknown descriptor write request");
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
                }
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_up = findViewById(R.id.btn_up);
        btn_up.setOnTouchListener(this);
        btn_down = findViewById(R.id.btn_down);
        btn_down.setOnTouchListener(this);
        btn_left = findViewById(R.id.btn_left);
        btn_left.setOnTouchListener(this);
        btn_right = findViewById(R.id.btn_right);
        btn_right.setOnTouchListener(this);

        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        startAdvertising();
        startGattServer();
    }

    @SuppressWarnings("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int STOP = 0;
        final int UP = 1;
        final int DOWN = 2;
        final int LEFT = 3;
        final int RIGHT = 4;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            switch (v.getId()) {
                case R.id.btn_up:
                    direction = UP;
                    break;
                case R.id.btn_down:
                    direction = DOWN;
                    break;
                case R.id.btn_left:
                    direction = LEFT;
                    break;
                case R.id.btn_right:
                    direction = RIGHT;
                    break;
                default:
                    direction = STOP;
            }
        }
        else if (event.getAction() == MotionEvent.ACTION_UP) {
            direction = STOP;
        }

        notifyRegisteredDevices();
        return true;
    }

    public void startGattServer() {
        gattServer = bluetoothManager.openGattServer(this, bluetoothGattServerCallback);
        gattServer.addService(createGattService());
    }

    public void startAdvertising() {
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        BluetoothLeAdvertiser bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private BluetoothGattService createGattService() {
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic direction = new BluetoothGattCharacteristic(CHARACTERISTIC_DIRECTION_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PROPERTY_READ);

        BluetoothGattDescriptor directionConfig = new BluetoothGattDescriptor(DESCRIPTOR_CONFIG_UUID,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        direction.addDescriptor(directionConfig);

        service.addCharacteristic(direction);

        return service;
    }

    private void notifyRegisteredDevices() {
        BluetoothGattCharacteristic characteristic = gattServer
                .getService(SERVICE_UUID)
                .getCharacteristic(CHARACTERISTIC_DIRECTION_UUID);

        for (BluetoothDevice device : devices) {
            byte[] value = toByteArray(direction);
            characteristic.setValue(value);
            gattServer.notifyCharacteristicChanged(device, characteristic, false);
        }
    }

    public static byte[] toByteArray(int value) {
        return new byte[]{
                (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value
        };
    }
}
