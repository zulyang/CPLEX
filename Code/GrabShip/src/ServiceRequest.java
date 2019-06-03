import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author  Team GrabShip
 */

public class ServiceRequest implements Comparable<ServiceRequest>{
	
	private String ServiceRequestID;
    private double FuelRequired;
    private String LocName;
    private String time;
    private String MMSI;
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public ServiceRequest() {
		
	}
    
    public ServiceRequest(String serviceRequestID, double fuelRequired, String locName, String time, String mmsi) {
		ServiceRequestID = serviceRequestID;
		FuelRequired = fuelRequired;
		LocName = locName;
		this.time = time;
		this.MMSI = mmsi;
	}
    
	public String getRequestID() {
		return ServiceRequestID;
	}
	
	public void setRequestID(String requestID) {
		ServiceRequestID = requestID;
	}
	
	public double getFuelRequired() {
		return FuelRequired;
	}
	
	public void setFuelRequired(double fuelRequired) {
		FuelRequired = fuelRequired;
	}
	
	public String getLocName() {
		return LocName;
	}
	
	public void setLocName(String locName) {
		LocName = locName;
	}
	
	public String getTime() {
		return time;
	}
	
	public void setTime(String time) {
		this.time = time;
	}
	
	public String getServiceRequestID() {
		return ServiceRequestID;
	}

	public void setServiceRequestID(String serviceRequestID) {
		ServiceRequestID = serviceRequestID;
	}

	public String getMMSI() {
		return MMSI;
	}

	public void setMMSI(String mMSI) {
		MMSI = mMSI;
	}
	
	@Override
	public int compareTo(ServiceRequest o) {
		LocalDateTime thisDateTime = LocalDateTime.parse(getTime(), formatter);
		LocalDateTime otherDateTime = LocalDateTime.parse(o.getTime(), formatter);
	    return thisDateTime.compareTo(otherDateTime);
	}
	
	public String toString() {
        return  "\nService Request ID: " + this.ServiceRequestID + 
                "\nFuel Required: " + this.FuelRequired + 
                "\nLocation Name: " + this.LocName + 
                "\nRequested Time: " + this.time + 
                "\nMMSI: " + this.MMSI;
    }
	
}