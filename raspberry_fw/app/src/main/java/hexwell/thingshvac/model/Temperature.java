package hexwell.thingshvac.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Temperature {
	@SerializedName("wanted")
	@Expose
	private Integer wanted;
	@SerializedName("current")
	@Expose
	private Integer current;
	@SerializedName("from_device")
	@Expose
	private Boolean fromDevice;

	public Temperature(int current, int wanted) {
		this.current = current;
		this.wanted = wanted;
		this.fromDevice = true;
	}

	public Integer getWanted() {
		return wanted;
	}

	public void setWanted(Integer wanted) {
		this.wanted = wanted;
	}

	public Integer getCurrent() {
		return current;
	}

	public void setCurrent(Integer current) {
		this.current = current;
	}

	public Boolean getFromDevice() {
		return fromDevice;
	}

	public void setFromDevice(Boolean fromDevice) {
		this.fromDevice = fromDevice;
	}
}
