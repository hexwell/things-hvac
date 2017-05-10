package hexwell.thingshvac.service;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class TemperatureFirebaseMessagingService extends FirebaseMessagingService {
	private LocalBroadcastManager broadcaster;

	@Override
	public void onCreate() {
		broadcaster = LocalBroadcastManager.getInstance(this);
	}

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		if (remoteMessage.getData().size() > 0) {
			Intent intent = new Intent("ServerUpdatedTemp");
			intent.putExtra("wanted", remoteMessage.getData().get("wanted"));
			broadcaster.sendBroadcast(intent);
		}
	}
}
