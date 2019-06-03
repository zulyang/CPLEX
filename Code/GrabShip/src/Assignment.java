import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.google.gson.JsonObject;

/**
 *
 * @author  Team GrabShip
 */

public class Assignment implements Comparable<Assignment>{
	
	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	
	private String MMSI;
	private String Action;
	private String Destination;
    private String DepartTime;
    private int Fuel;
    
	public Assignment(String mMSI, String action, String destination, String departTime, int fuel) {
		MMSI = mMSI;
		Action = action;
		Destination = destination;
		DepartTime = departTime;
		Fuel = fuel;
	}

	public String getMMSI() {
		return MMSI;
	}

	public void setMMSI(String mMSI) {
		MMSI = mMSI;
	}

	public String getAction() {
		return Action;
	}

	public void setAction(String action) {
		Action = action;
	}

	public String getDestination() {
		return Destination;
	}

	public void setDestination(String destination) {
		Destination = destination;
	}

	public String getDepartTime() {
		return DepartTime;
	}

	public void setDepartTime(String departTime) {
		DepartTime = departTime;
	}

	public int getFuel() {
		return Fuel;
	}

	public void setFuel(int fuel) {
		Fuel = fuel;
	}
	
	@Override
	public int compareTo(Assignment o) {
		LocalDateTime thisDateTime = LocalDateTime.parse(getDepartTime(), formatter);
		LocalDateTime otherDateTime = LocalDateTime.parse(o.getDepartTime(), formatter);
	    return thisDateTime.compareTo(otherDateTime);
	}
	
	public JsonObject toJSON() {
		JsonObject assignmentJson = new JsonObject();
		assignmentJson.addProperty("MMSI", this.MMSI);
		assignmentJson.addProperty("Action", this.Action);
		assignmentJson.addProperty("Destination", this.Destination);
		assignmentJson.addProperty("DepartTime", this.DepartTime);
		assignmentJson.addProperty("Fuel", this.Fuel);
		
		return assignmentJson;
	}
	
	public String toString() {
        return  "\nMMSI: " + this.MMSI + 
        		"\nAction: " + this.Action +
                "\nDestination: " + this.Destination + 
                "\nDepartTime: " + this.DepartTime + 
                "\nFuel: " + this.Fuel;
    }
	
}