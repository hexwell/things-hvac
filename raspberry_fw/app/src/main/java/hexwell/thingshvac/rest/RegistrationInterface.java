package hexwell.thingshvac.rest;

import hexwell.thingshvac.model.Token;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface RegistrationInterface {
	@POST("/register/")
	Call<Token> register(@Body Token data);
}
