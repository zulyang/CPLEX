/**
 *
 * @author  Team GrabShip
 */

public class Location {
	
	private String LocationName;
	private int BKCapacity;
	private String LocationType;
    private boolean isBunkerHome;
    private boolean isServiceLocation;
    
    public Location() {
    	
    }
    
    public Location(String locationName, int BKCapacity, String locationType, boolean isBunkerHome, boolean isServiceLocation) {
		this.LocationName = locationName;
		this.BKCapacity = BKCapacity;
		this.LocationType = locationType;
		this.isBunkerHome = isBunkerHome;
		this.isServiceLocation = isServiceLocation;
	}

	public String getLocationName() {
		return LocationName;
	}
	
	public void setLocationName(String locationName) {
		LocationName = locationName;
	}
	
	public int getBKCapacity() {
		return BKCapacity;
	}

	public void setBKCapacity(int bKCapacity) {
		BKCapacity = bKCapacity;
	}
	
	public String getLocationType() {
		return LocationType;
	}
	
	public void setLocationType(String locationType) {
		LocationType = locationType;
	}
	
	public boolean isBunkerHome() {
		return isBunkerHome;
	}
	
	public void setBunkerHome(boolean isBunkerHome) {
		this.isBunkerHome = isBunkerHome;
	}
	
	public boolean isServiceLocation() {
		return isServiceLocation;
	}
	
	public void setServiceLocation(boolean isServiceLocation) {
		this.isServiceLocation = isServiceLocation;
	}
	
	public String toString() {
        return  "\nLocation Name: " + this.LocationName + 
        		"\nBK Capacity: " + this.BKCapacity +
                "\nLocation Type: " + this.LocationType + 
                "\nIs Bunker Home: " + this.isBunkerHome + 
                "\nIs Service Location: " + this.isServiceLocation;
    }
	
}