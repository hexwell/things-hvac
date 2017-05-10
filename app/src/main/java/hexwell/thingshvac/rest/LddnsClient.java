package hexwell.thingshvac.rest;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LddnsClient {
	private static final String BASE_URL = "https://things-hvac-lddns-hexwell.c9users.io";
	private static Retrofit retrofit = null;

	public static Retrofit getClient() {
		if (retrofit==null) {
			retrofit = new Retrofit.Builder()
					.baseUrl(BASE_URL)
					.addConverterFactory(GsonConverterFactory.create())
					.build();
		}
		return retrofit;
	}
}
