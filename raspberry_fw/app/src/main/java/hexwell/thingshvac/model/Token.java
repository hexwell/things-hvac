package hexwell.thingshvac.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Token{
	@SerializedName("token")
	@Expose
	private String token;

	public Token(String current) {
		this.token = current;
	}

	public String getToken() {
		return token;
	}
}
