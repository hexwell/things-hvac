package hexwell.thingshvac.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Ip{
	@SerializedName("ip")
	@Expose
	private String ip;

	public String getIp() {
		return ip;
	}
}
