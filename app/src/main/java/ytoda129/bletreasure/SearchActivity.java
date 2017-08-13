package ytoda129.bletreasure;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class SearchActivity extends AppCompatActivity {

    private static final long READ_RSSI_PERIOD = 1000; // msec

    private BluetoothAdapter mAdapter;
    private BluetoothGatt mBluetoothGatt;
    private TextView mCloseness;
    private TextView mRssiText;
    private Handler mHandler = new Handler();

    private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Disonnected", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("SearchActivity", "onReadRemoteRssi rssi=" + String.valueOf(rssi));
                setClosenessText(rssi);
            }
        }
    };

    private BluetoothAdapter.LeScanCallback mScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            // SensorTag以外のBLE端末は無視
            if (device.getName() == null || !device.getName().contains("SensorTag")) {
                return;
            }

            String msg = "name=" + device.getName() + ", bondStatus="
                    + device.getBondState() + ", address="
                    + device.getAddress() + ", type" + device.getType()
                    + ", rssi=" + rssi;
            Log.i("SearchActivity", msg);

            if (mBluetoothGatt == null) {
                // SensorTagは120sec経過するとアドバタイズをやめてしまうので接続しておく
                mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mBluetoothGattCallback);

                // READ_RSSI_PERIOD(=1sec)間隔でRSSIを取得する
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mBluetoothGatt == null) {
                            return;
                        }
                        mBluetoothGatt.readRemoteRssi();
                        mHandler.postDelayed(this, READ_RSSI_PERIOD);
                    }
                }, READ_RSSI_PERIOD);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mAdapter = manager.getAdapter();

        mCloseness = (TextView) findViewById(R.id.closeness);
        mRssiText = (TextView) findViewById(R.id.rssiText);

        // 表示中に勝手にスリープに入らないようにする
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 画面表示時のみスキャン実行
        mAdapter.startLeScan(mScanCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.stopLeScan(mScanCallback);
        mBluetoothGatt.disconnect();
        mBluetoothGatt = null;
        mHandler.removeCallbacksAndMessages(null);
    }

    private void setClosenessText(int rssi) {
        String rssiStr = String.valueOf(rssi);
        int closenessId = R.string.closeness_very_far;

        // 近さは4段階
        if (rssi > -50) {
            closenessId = R.string.closeness_very_near;
        } else if (-50 >= rssi && rssi > -65) {
            closenessId = R.string.closeness_near;
        } else if (-65 >= rssi && rssi > -80) {
            closenessId = R.string.closeness_far;
        } else {
            closenessId = R.string.closeness_very_far;
        }

        runOnUiThread(new UpdateTextRunnable(rssiStr, closenessId));
    }

    private class UpdateTextRunnable implements Runnable {
        private String mRssiStr;
        private int mClosenessId;

        public UpdateTextRunnable(String rssiStr, int closenessId) {
            mRssiStr = rssiStr;
            mClosenessId = closenessId;
        }

        @Override
        public void run() {
            mRssiText.setText(mRssiStr);
            mCloseness.setText(mClosenessId);
        }
    }
}
