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
import android.view.View;
import android.widget.Button;

import com.harrysoft.joystickview.JoystickView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements JoystickView.JoystickListener {

    Button btn_connect;
    private BluetoothManager bluetoothManager; // Used to get bluetoothAdapter and gattServer
    private BluetoothAdapter bluetoothAdapter; // Used to get bluetoothLeAdvertiser
    private BluetoothGattServer gattServer; // Used to send ble data
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;

    public int direction = 0; // This gets sent to all subscribed devices (int represents a direction)
    boolean connected = false;

    final int STOP = 0;
    final int UP = 1;
    final int DOWN = 2;
    final int LEFT = 3;
    final int RIGHT = 4;
    final int DISCONNECT = 5;

    private static final String TAG = "MainActivity";

    UUID SERVICE_UUID = UUID.fromString("6f4cee11-d76c-49a5-b2d5-5b91e1db87b2");
    UUID CHARACTERISTIC_DIRECTION_UUID = UUID.fromString("7dbfd27a-b283-4ea3-a90d-75c58aea3511");
    UUID DESCRIPTOR_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Set<BluetoothDevice> devices = new HashSet<>();

    // Callback used for gattServer advertising
    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settings) {
            Log.i(TAG, "Advertise Start Success");
        }

        @Override
        public void onStartFailure(int error) {
            Log.w(TAG, "uh oh, ERROR: " + error);
        }

    };
    // Callback used for gattServer
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

        JoystickView joystick = findViewById(R.id.joystick);
        joystick.setJoystickListener(this);

        btn_connect = findViewById(R.id.btn_connect);
        btn_connect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!connected) {
                    startAdvertising();
                    startGattServer();
                    btn_connect.setText("Disconnect");
                    connected = true;
                }
                else {
                    direction = DISCONNECT;
                    notifyRegisteredDevices();
                    stopGattServer();
                    stopAdvertising();
                    btn_connect.setText("Connect");
                    connected = false;
                }
            }
        });

        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter.isEnabled()) {
            direction = 5;
            notifyRegisteredDevices();
            stopGattServer();
            stopAdvertising();
        }
    }

    public void startGattServer() {
        gattServer = bluetoothManager.openGattServer(this, bluetoothGattServerCallback);
        gattServer.addService(createGattService());
    }

    public void stopGattServer() {
        if (gattServer == null) {
            return;
        }
        gattServer.close();
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

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
    }

    public void stopAdvertising() {
        if (bluetoothLeAdvertiser == null) {
            return;
        }
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
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
        if (gattServer != null) {
            BluetoothGattCharacteristic characteristic = gattServer
                    .getService(SERVICE_UUID)
                    .getCharacteristic(CHARACTERISTIC_DIRECTION_UUID);


            for (BluetoothDevice device : devices) {
                byte[] value = toByteArray(direction);
                characteristic.setValue(value);
                gattServer.notifyCharacteristicChanged(device, characteristic, false);
            }
        } else {
            System.out.println("GattServer not initialized");
        }
    }

    public static byte[] toByteArray(int value) {
        return new byte[]{
                (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value
        };
    }

    @Override
    public void onJoystickMoved(float xPercent, float yPercent, int id) {
        System.out.println("x: " + xPercent + ", y: " + yPercent);

        // Joystick is more horizontal than vertical
        if (Math.abs(xPercent) > Math.abs(yPercent)) {
            if (xPercent < 0) {
                direction = LEFT;
            } else if (xPercent > 0) {
                direction = RIGHT;
            }
        }
        // Joystick is more vertical than horizontal
        else if (Math.abs(xPercent) < Math.abs(yPercent)) {
            if (yPercent < 0) {
                direction = UP;
            } else if (yPercent > 0) {
                direction = DOWN;
            }
        } else {
            direction = STOP;
        }

        notifyRegisteredDevices();

    }
}
