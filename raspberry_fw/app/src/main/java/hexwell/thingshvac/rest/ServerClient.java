package hexwell.thingshvac.rest;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class ServerClient {
	private static Retrofit retrofit = null;

	public static Retrofit getClient(String ip) {
		if (retrofit==null) {
			retrofit = new Retrofit.Builder()
					.baseUrl("http://" + ip)
					.addConverterFactory(GsonConverterFactory.create())
					.build();
		}
		return retrofit;
	}
}
