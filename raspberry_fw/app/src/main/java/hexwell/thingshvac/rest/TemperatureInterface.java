package hexwell.thingshvac.rest;

import hexwell.thingshvac.model.Temperature;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface TemperatureInterface {
	@POST("/data/")
	Call<Temperature> sendTemperature(@Body Temperature data);
}
