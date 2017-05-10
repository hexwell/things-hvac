package hexwell.thingshvac.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;
import com.google.firebase.iid.FirebaseInstanceId;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import hexwell.thingshvac.model.Ip;
import hexwell.thingshvac.model.Temperature;
import hexwell.thingshvac.model.Token;
import hexwell.thingshvac.rest.IpInterface;
import hexwell.thingshvac.rest.LddnsClient;
import hexwell.thingshvac.rest.RegistrationInterface;
import hexwell.thingshvac.rest.ServerClient;
import hexwell.thingshvac.rest.TemperatureInterface;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

//TODO TEMP REQUESTS ERROR HANDLING & ATTEMPTS

public class HomeActivity extends Activity {
	private static final String TAG = HomeActivity.class.getSimpleName();
	private static final char TERMINATOR = ';';
	private static final int MAX_CMD_LEN = 4;

	// UART Configuration Parameters
	private static final int BAUD_RATE = 115200;
	private static final int DATA_BITS = 8;
	private static final int STOP_BITS = 1;

	private static final int CHUNK_SIZE = 512;

	private PeripheralManagerService manager = new PeripheralManagerService();
	private UartDevice arduinoDevice;

	private Runnable uartReceiverRunnable = new Runnable() {
		@Override
		public void run() {
			receiveUartData();
		}
	};

	// Thermostat main variables
	private int current;
	private int wanted;
	private int hvacStatus;

	//WiFi status
	private boolean connected = false;

	//Communication with server
	private IpInterface ipService = LddnsClient.getClient().create(IpInterface.class);
	private TemperatureInterface temperatureService;

	// Worker and handler for long tasks
	private HandlerThread taskThread;
	private Handler taskHandler;

	// Logic handling
	Queue<String> cmdsQueue = new ConcurrentLinkedQueue<>();

	private Runnable handleQueuedCmds = new Runnable() {
		@Override
		public void run() {
			while(!cmdsQueue.isEmpty()){
				String cmd = cmdsQueue.poll();
				if (!cmd.equals("requestIp")){
					handleCmd(cmd);
				} else {
					Log.d(TAG + " Queue Handler", "Requested IP");

					Call<Ip> ipCall = ipService.getIp();
					ipCall.enqueue(requestIp);
				}
			}
		}
	};

	private void handleCmd(String full_cmd){
		char cmd = full_cmd.charAt(0);

		Log.d(TAG + " Cmd handler", "Handling command: " + full_cmd);

		switch(cmd){
			case 's':
				sendUartData(""); //cleaning boot garbage
				sendUartData("s");
				break;
			case 'i':
				sendUartData("i" + (connected ? '1' : '0'));
				break;
			case 'u':
				setHvacStatus();
				sendUartData(String.format(Locale.ENGLISH, "w%02d", wanted));
				sendUartData(String.format(Locale.ENGLISH, "h%d", hvacStatus));
				handleCmd("i");
				break;
			case 'c':
				current = Integer.parseInt(full_cmd.substring(1));
				setHvacStatus();
				sendData();
				handleCmd("u");
				break;
			case 'w':
				wanted = Integer.parseInt(full_cmd.substring(1));
				setHvacStatus();
				sendData();
				handleCmd("u");
				break;
		}
	}

	private void setHvacStatus(){
		if(current == wanted){
			hvacStatus = 0;
		} else if (current < wanted){
			hvacStatus = 1;
		} else if (current > wanted){
			hvacStatus = 2;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d(TAG, "Home activity created");

		// Create a background looper thread for I/O and logic
		taskThread = new HandlerThread("CommThread");
		taskThread.start();
		taskHandler = new Handler(taskThread.getLooper());

		// Attempt to access the UART device
		try {
			openUart("UART0", BAUD_RATE);
			// Read any initially buffered data
			taskHandler.post(uartReceiverRunnable);
		} catch (IOException e) {
			Log.e(TAG, "Unable to open UART device", e);
		}

		cmdsQueue.offer("s");
		taskHandler.post(handleQueuedCmds);
	}

	@Override
	protected void onStart() {
		super.onStart();

		//Receiver for FCM msgs
		LocalBroadcastManager.getInstance(this).registerReceiver((tempBroadcastReceiver),
				new IntentFilter("ServerUpdatedTemp")
		);

		//Receiver for WiFi Connections/Disconnections
		this.registerReceiver(this.wifiReceiver, new IntentFilter(
				WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
	}

	@Override
	protected void onStop() {
		super.onStop();

		// Unregister broadcast receivers
		LocalBroadcastManager.getInstance(this).unregisterReceiver(tempBroadcastReceiver);
		this.unregisterReceiver(wifiReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// Terminate the worker thread
		if (taskThread != null) {
			taskThread.quitSafely();
		}

		// Attempt to close the UART device
		try {
			closeUart();
		} catch (IOException e) {
			Log.e(TAG, "Error closing UART device:", e);
		}
	}

	private BroadcastReceiver wifiReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context context, Intent intent) {
			SupplicantState newState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);

			if(newState == SupplicantState.ASSOCIATED || newState == SupplicantState.COMPLETED) {
				if(!connected){
					connected = true;
					cmdsQueue.offer("requestIp");
					cmdsQueue.offer("i");
					taskHandler.post(handleQueuedCmds);
				}
			}else if(newState == SupplicantState.DISCONNECTED) {
				if (connected) {
					connected = false;
					cmdsQueue.offer("i");
					taskHandler.post(handleQueuedCmds);
				}
			}
		}};

	private BroadcastReceiver tempBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			wanted = Integer.parseInt(intent.getExtras().getString("wanted"));
			Log.d(TAG + " Firebase", "Received new wanted: " + wanted);
			cmdsQueue.offer("u");
			taskHandler.post(handleQueuedCmds);
		}
	};

	private Callback<Token> registerToken = new Callback<Token>() {
		@Override
		public void onResponse(Call<Token> call, Response<Token> response) {
			Log.d(TAG + " Token registration", response.message());
		}

		@Override
		public void onFailure(Call<Token> call, Throwable t) {
			// Log error here since request failed
			Log.e(TAG, t.toString());
		}
	};

	private Callback<Ip> requestIp = new Callback<Ip>() {
		@Override
		public void onResponse(Call<Ip> call, Response<Ip> response) {
			Ip body = response.body();

			Log.d(TAG + " IP Request", response.message());

			if(body != null){
				String ip = body.getIp();

				ServerClient.getClient(ip).create(RegistrationInterface.class).register(
						new Token(FirebaseInstanceId.getInstance().getToken())).enqueue(
						registerToken);

				temperatureService = ServerClient.getClient(ip).create(
						TemperatureInterface.class);
				sendData();
			} else {
				cmdsQueue.offer("requestIp");
				taskHandler.postDelayed(handleQueuedCmds, 10000);
			}
		}

		@Override
		public void onFailure(Call<Ip> call, Throwable t) {
			// Log error here since request failed
			Log.e(TAG, t.toString());

			cmdsQueue.offer("requestIp");
			taskHandler.postDelayed(handleQueuedCmds, 10000);
		}
	};

	private Callback<Temperature> requestTemperature = new Callback<Temperature>() {
		@Override
		public void onResponse(Call<Temperature> call, Response<Temperature> response) {
			Log.d(TAG + " Sending data to server", response.message());
		}

		@Override
		public void onFailure(Call<Temperature> call, Throwable t) {
			// Log error here since request failed
			Log.e(TAG, t.toString());
		}
	};

	private void sendData(){
		if(temperatureService != null) {
			Call<Temperature> temperatureCall = temperatureService.sendTemperature(
					new Temperature(current, wanted));
			temperatureCall.enqueue(requestTemperature);
		}
	}

	/**
	 * Callback invoked when UART receives new incoming data.
	 */
	private UartDeviceCallback arduinoAvailableCallback = new UartDeviceCallback() {
		@Override
		public boolean onUartDeviceDataAvailable(UartDevice uart) {
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

		arduinoDevice.registerUartDeviceCallback(arduinoAvailableCallback, taskHandler);
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
					String decoded = (new String(buffer)).substring(0, read);

					for(int i = 0; i < decoded.length(); i++){
						if(decoded.charAt(i) == TERMINATOR){
							handleCmd(decoded.substring(0, i));
						}
						if(i >= MAX_CMD_LEN){ decoded = decoded.substring(i); i = 0; }
					}
				}
			} catch (IOException e) {
				Log.w(TAG, "Unable to transfer data over UART", e);
			}
		}
	}

	private void sendUartData(String data){
		try {
			data += TERMINATOR;
			byte[] encoded_data = data.getBytes("US-ASCII");
			arduinoDevice.write(encoded_data, encoded_data.length);
		} catch (UnsupportedEncodingException e) {
			Log.w(TAG, "Unable to get bytes", e);
		} catch (IOException e) {
			Log.w(TAG, "Unable to send to Arduino", e);
		}

	}
}
