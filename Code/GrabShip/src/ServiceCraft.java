/**
 *
 * @author  Team GrabShip
 */

public class ServiceCraft {

	private String MMSI;
	private String LocationName;
	private double ETA;
	private String Destination;
	private String Status;
	private int Capacity;
	private int FlowRate;
	private int CurrentHold;
	private String PumpStatus;
	private String departTime;

	public ServiceCraft(String mMSI, String locationName, double eTA, String destination, String status, int capacity,
			int flowRate, int currentHold, String pumpStatus) {
		MMSI = mMSI;
		LocationName = locationName;
		ETA = eTA;
		Destination = destination;
		Status = status;
		Capacity = capacity;
		FlowRate = flowRate;
		CurrentHold = currentHold;
		PumpStatus = pumpStatus;
	}
	
	public String getMMSI() {
		return MMSI;
	}
	
	public void setMMSI(String mMSI) {
		MMSI = mMSI;
	}
	
	public String getLocationName() {
		return LocationName;
	}
	
	public void setLocationName(String locationName) {
		LocationName = locationName;
	}
	
	public double getETA() {
		return ETA;
	}
	
	public void setETA(double eTA) {
		ETA = eTA;
	}
	
	public String getDestination() {
		return Destination;
	}
	
	public void setDestination(String destination) {
		Destination = destination;
	}
	
	public String getStatus() {
		return Status;
	}
	
	public void setStatus(String status) {
		Status = status;
	}
	
	public int getCapacity() {
		return Capacity;
	}
	
	public void setCapacity(int capacity) {
		Capacity = capacity;
	}
	
	public int getFlowRate() {
		return FlowRate;
	}
	
	public void setFlowRate(int flowRate) {
		FlowRate = flowRate;
	}
	
	public int getCurrentHold() {
		return CurrentHold;
	}
	
	public void setCurrentHold(int currentHold) {
		CurrentHold = currentHold;
	}
	
	public String getPumpStatus() {
		return PumpStatus;
	}
	
	public void setPumpStatus(String pumpStatus) {
		PumpStatus = pumpStatus;
	}
	
	public String getDepartTime() {
		return departTime;
	}

	public void setDepartTime(String departTime) {
		this.departTime = departTime;
	}
	
	public double getServiceTime(ServiceRequest sr) {
		double serviceTime = Double.valueOf(sr.getFuelRequired()) / Double.valueOf(this.FlowRate) ;
		return serviceTime;
	}
	
	public String toString() {
        return  "\nMMSI: " + this.MMSI + 
                "\nLocation Name: " + this.LocationName + 
                "\nETA: " + this.ETA + 
                "\nDestination: " + this.Destination + 
                "\nStatus: " + this.Status +
                "\nCapacity: " + this.Capacity + 
                "\nFlow Rate: " + this.FlowRate + 
                "\nCurrent Hold: " + this.CurrentHold + 
                "\nPump Status: " + this.PumpStatus;
    }
	
}