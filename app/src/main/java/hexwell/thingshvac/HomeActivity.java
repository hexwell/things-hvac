package hexwell.thingshvac;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;


public class HomeActivity extends AppCompatActivity {
	private static final String TAG = "HomeActivityLog";

	// Temperature main variables
	private int current;
	private int wanted;

	// UART Configuration Parameters
	private static final int BAUD_RATE = 115200;
	private static final int DATA_BITS = 8;
	private static final int STOP_BITS = 1;

	private static final int CHUNK_SIZE = 512;

	private HandlerThread arduinoInputThread;
	private Handler arduinoInputHandler;

	private PeripheralManagerService manager = new PeripheralManagerService();
	private UartDevice arduinoDevice;

	private Runnable mTransferUartRunnable = new Runnable() {
		@Override
		public void run() {
			receiveUartData();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
		Log.d(TAG, "TOKEN: " + FirebaseInstanceId.getInstance().getToken()); // todo send to server

		//Receiver for FCM msgs
		LocalBroadcastManager.getInstance(this).registerReceiver((tempBroadcastReceiver),
				new IntentFilter("ServerUpdatedTemp")
		);

		// Create a background looper thread for I/O
		arduinoInputThread = new HandlerThread("InputThread");
		arduinoInputThread.start();
		arduinoInputHandler = new Handler(arduinoInputThread.getLooper());

		// Attempt to access the UART device
		try {
			openUart("UART0", BAUD_RATE);
			// Read any initially buffered data
			arduinoInputHandler.post(mTransferUartRunnable);
		} catch (IOException e) {
			Log.e(TAG, "Unable to open UART device", e);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// Unregister broadcast receiver
		LocalBroadcastManager.getInstance(this).unregisterReceiver(tempBroadcastReceiver);

		// Terminate the worker thread
		if (arduinoInputThread != null) {
			arduinoInputThread.quitSafely();
		}

		// Attempt to close the UART device
		try {
			closeUart();
		} catch (IOException e) {
			Log.e(TAG, "Error closing UART device:", e);
		}
	}

	private BroadcastReceiver tempBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			wanted = Integer.parseInt(intent.getExtras().getString("wanted"));
			Log.d(TAG, "New wanted temp = " + wanted); //todo remove this
			String command = String.format(Locale.ENGLISH, "w%02d;", wanted);
			try {
				byte[] new_data = command.getBytes("US-ASCII");
				arduinoDevice.write(new_data, new_data.length); // todo decouple this in separ. func
			} catch (UnsupportedEncodingException e) {
				Log.w(TAG, "Unable to get bytes", e);
			} catch (IOException e) {
				Log.w(TAG, "Unable to send to Arduino", e);
			}
			// todo post logic callback to handler
		}
	};

	/**
	 * Callback invoked when UART receives new incoming data.
	 */
	private UartDeviceCallback arduinoAvailableCallback = new UartDeviceCallback() {
		@Override
		public boolean onUartDeviceDataAvailable(UartDevice uart) {
			// Queue up a data transfer
			receiveUartData();
			//Continue listening for more interrupts
			return true;
		}

		@Override
		public void onUartDeviceError(UartDevice uart, int error) {
			Log.w(TAG, uart + ": Error event " + error);
		}
	};

    /* Private Helper Methods */

	/**
	 * Access and configure the requested UART device for 8N1.
	 *
	 * @param name Name of the UART peripheral device to open.
	 * @param baudRate Data transfer rate. Should be a standard UART baud,
	 *                 such as 9600, 19200, 38400, 57600, 115200, etc.
	 *
	 * @throws IOException if an error occurs opening the UART port.
	 */
	private void openUart(String name, int baudRate) throws IOException {
		arduinoDevice = manager.openUartDevice(name);
		// Configure the UART
		arduinoDevice.setBaudrate(baudRate);
		arduinoDevice.setDataSize(DATA_BITS);
		arduinoDevice.setParity(UartDevice.PARITY_NONE);
		arduinoDevice.setStopBits(STOP_BITS);

		arduinoDevice.registerUartDeviceCallback(arduinoAvailableCallback, arduinoInputHandler);
	}

	/**
	 * Close the UART device connection, if it exists
	 */
	private void closeUart() throws IOException {
		if (arduinoDevice != null) {
			arduinoDevice.unregisterUartDeviceCallback(arduinoAvailableCallback);
			try {
				arduinoDevice.close();
			} finally {
				arduinoDevice = null;
			}
		}
	}

	/**
	 * Loop over the contents of the UART RX buffer, transferring each
	 * one back to the TX buffer to create a loopback service.
	 *
	 * Potentially long-running operation. Call from a worker thread.
	 */
	private void receiveUartData() {
		if (arduinoDevice != null) {
			// Loop until there is no more data in the RX buffer.
			try {
				byte[] buffer = new byte[CHUNK_SIZE];
				int read;
				while ((read = arduinoDevice.read(buffer, buffer.length)) > 0) {
					Log.d(TAG, "data incoming: " + read); // todo remove this
					// todo decode and add to queue, if needed post logic callback
				}
			} catch (IOException e) {
				Log.w(TAG, "Unable to transfer data over UART", e);
			}
		}
	}
}


class MyFirebaseInstanceIDService extends FirebaseInstanceIdService{
	private static final String TAG = "FirebaseRegistration";

	@Override
	public void onTokenRefresh() {
		// Get updated InstanceID token.
		String refreshedToken = FirebaseInstanceId.getInstance().getToken();
		Log.d(TAG, "Refreshed token: " + refreshedToken);

		// If you want to send messages to this application instance or
		// manage this apps subscriptions on the server side, send the
		// Instance ID token to your app server.
		//sendRegistrationToServer(refreshedToken); TODO(developer)
	}
}
