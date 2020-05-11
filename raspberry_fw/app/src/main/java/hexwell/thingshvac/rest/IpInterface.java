package hexwell.thingshvac.rest;

import hexwell.thingshvac.model.Ip;
import retrofit2.Call;
import retrofit2.http.GET;

public interface IpInterface {
	@GET("/")
	Call<Ip> getIp();
}
