import ilog.concert.*;
import ilog.cplex.*;
import java.io.*;
import java.sql.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import com.google.gson.*;

/**
 *
 * @author  Team GrabShip
 */

public class GrabShipVRPTW {
	private static final String SERVER_LOCATION = "http://127.0.0.1:8080/";
	private static final String USER_AGENT = "application/json";
	
	public static void main(String[] args) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		// Get current time in simulation
		String currentTimeGET = "getCurrentTime";
		String currentTimeResponse = getRequest(currentTimeGET);
		String currentTime = currentTimeJsonToString(gson, currentTimeResponse);
		
		// Get Location List
		String locGET = "getLocationList";
		String locResponse = getRequest(locGET);
		ArrayList<Location> locationList = locationJsonToLocationList(gson, locResponse);
		
		// Get ServiceRequest List
		int lastRequestID = 0;
		String serviceRequestGET = "getServiceDetail";
		String serviceRequestResponse = getRequest(serviceRequestGET);
		List<ServiceRequest> serviceRequestList = serviceRequestJsonToServiceRequestList(gson, serviceRequestResponse);
		//serviceRequestList.add(0, new ServiceRequest());
		
		// Sort by requested date
		Collections.sort(serviceRequestList);
		
		for(ServiceRequest sr : serviceRequestList) {
			System.out.println(sr.toString());
		}
		
		// Get all Service Craft
		String serviceCraftGET = "getServiceVessel";
		String serviceCraftResponse = getRequest(serviceCraftGET);
		ArrayList<ServiceCraft> serviceCraftList = serviceCraftJsonToServiceCraftList(gson, serviceCraftResponse);
		// Remove all non-idling service crafts
		for(ServiceCraft sc : serviceCraftList) {
			if(!sc.getStatus().equalsIgnoreCase("Idle")) {
				serviceCraftList.remove(sc);
			}	else {
				sc.setDepartTime(currentTime);
			}
		}
		
		System.out.println("Current time: " + currentTime);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime currentDateTime = LocalDateTime.parse(currentTime, formatter);
		
		/*
		// Get all service requests which time requested is 2 hours before current time
		for(ServiceRequest sr : serviceRequestList) {
			LocalDateTime srDateTime = LocalDateTime.parse(sr.getTime(),formatter);
			if(srDateTime.isBefore(currentDateTime.minusHours(2))) {
				System.out.println(sr.toString());
			}
		}
		*/
		
		
		double[][][] totalServiceTimeArr = getTotalTravelTimeByVessel(gson, serviceCraftList, serviceRequestList);
		
		List<Assignment> assignmentList =  solveModel(gson, totalServiceTimeArr, serviceCraftList, 
				serviceRequestList, locationList, currentDateTime);
		
		// Sort by depart time
		Collections.sort(assignmentList);
		for(Assignment a : assignmentList) {
			System.out.println(a.toString());
		}
		System.out.println("Assignment size: " + assignmentList.size());
		
		
		// Need to feed in only home anchorage + depot
		
		
		// Convert JsonObject to pretty printed json String
		// String json = gson.toJson(location);
		// System.out.println(json);
	}
	
	// For calling GET requests
	@SuppressWarnings("deprecation")
	public static String getRequest(String GET) {
		HttpClient client = HttpClientBuilder.create().build();
		StringBuffer result = new StringBuffer();
		try {
			String url = SERVER_LOCATION + GET;

			HttpGet request = new HttpGet(url);

			// add request header
			request.addHeader("User-Agent", USER_AGENT);
			HttpResponse response = client.execute(request);

			//System.out.println("Response Code : " + response.getStatusLine().getStatusCode());

			BufferedReader rd = new BufferedReader(
				new InputStreamReader(response.getEntity().getContent()));

			String line = "";
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}
		}   catch(Exception e) {
            e.printStackTrace();
        }	finally {
			client.getConnectionManager().shutdown();
		}
		return result.toString();
	}
	
	// Convert location json list to location object list
	public static ArrayList<Location> locationJsonToLocationList(Gson gson, String locResponse) {
		// Convert json response to JsonElement
		JsonElement element = gson.fromJson(locResponse, JsonElement.class);
		// Convert JsonElement to JsonObject
		JsonObject jsonObj = element.getAsJsonObject();
		// Get the content of Result and Convert it to JsonObject
		JsonObject location = jsonObj.getAsJsonObject("Result");
		
		ArrayList<Location> locationList = new ArrayList<>();
		for(Map.Entry<String, JsonElement> entry : location.entrySet()) {
			JsonObject locDetails = entry.getValue().getAsJsonObject();
			
			String locationName = entry.getKey();
			int bkCapacity = 1;
			if(locDetails.get("BKCapaciy") != null) {
				bkCapacity = locDetails.get("BKCapaciy").getAsInt();
			}
			String locationType = locDetails.get("LocationType").getAsString();
			boolean isBunkerHome = locDetails.get("isBunkerHome").getAsBoolean();
			boolean isServiceLocation = locDetails.get("isServiceLocation").getAsBoolean();
			
			Location loc = new Location(locationName, bkCapacity, locationType, isBunkerHome, isServiceLocation);
			locationList.add(loc);
			//System.out.println(loc.toString());
		}
		return locationList;
	}
	
	// Convert service request json list to service request object list
	public static ArrayList<ServiceRequest> serviceRequestJsonToServiceRequestList(Gson gson, String reqResponse) {
		// Convert json response to JsonElement
		JsonElement element = gson.fromJson(reqResponse, JsonElement.class);
		// Convert JsonElement to JsonObject
		JsonObject jsonObj = element.getAsJsonObject();
		// Get the content of Result and Convert it to JsonObject
		JsonObject req = jsonObj.getAsJsonObject("Result");
		
		ArrayList<ServiceRequest> requestList = new ArrayList<>();
		for(Map.Entry<String, JsonElement> entry : req.entrySet()) {
			JsonObject reqDetails = entry.getValue().getAsJsonObject();
			
			String serviceRequestID = entry.getKey();
			double fuelRequired = reqDetails.get("FuelRequired").getAsDouble();
			String locationName = reqDetails.get("LocName").getAsString();
			String timeRequested = reqDetails.get("TimeRequested").getAsString();
			String mmsi = reqDetails.get("MMSI").getAsString();
			
			ServiceRequest request = new ServiceRequest(serviceRequestID, fuelRequired, locationName, timeRequested, mmsi);
			requestList.add(request);
			//System.out.println(request.toString());
		}
		return requestList;
	}
	
	// Convert current time json to current time string
	public static String currentTimeJsonToString(Gson gson, String currentTimeResponse) {
		// Convert json response to JsonElement
		JsonElement element = gson.fromJson(currentTimeResponse, JsonElement.class);
		// Convert JsonElement to JsonObject
		JsonObject jsonObj = element.getAsJsonObject();
		// Get the content of Result and Convert it to JsonObject
		JsonObject currentTimeObj = jsonObj.getAsJsonObject("Result");
		
		String currentTime = currentTimeObj.get("CurrentTime").getAsString();
		
		return currentTime;
	}
	
	// Convert service craft json list to service craft object list
	public static ArrayList<ServiceCraft> serviceCraftJsonToServiceCraftList(Gson gson, String serviceCraftResponse) {
		// Convert json response to JsonElement
		JsonElement element = gson.fromJson(serviceCraftResponse, JsonElement.class);
		// Convert JsonElement to JsonObject
		JsonObject jsonObj = element.getAsJsonObject();
		// Get the content of Result and Convert it to JsonObject
		JsonArray mmsiArray = jsonObj.getAsJsonArray("Result");
		
		ArrayList<ServiceCraft> serviceCraftList = new ArrayList<>();
		
		// Get list of mmsi from json array
		ArrayList<String> mmsiList = new ArrayList<>();
		for (JsonElement serviceCraftID : mmsiArray) {
		    mmsiList.add(serviceCraftID.getAsString());
		}
		
		String getVesselStatusGET = "getVesselStatus";
		String getServiceVesselDetailGET = "getServiceVesselDetail";
		String getServiceVesselStatusGET = "getServiceVesselStatus";
		// Call getVesselStatus, getServiceVesselDetail, getServiceVesselStatus 
		// and assign the properties to the ServiceCraft object
		for(String mmsi : mmsiList) {
			String vesselStatusResponse = getRequest(getVesselStatusGET + "?mmsi=" + mmsi);
			String serviceVesselDetailResponse = getRequest(getServiceVesselDetailGET + "?mmsi=" + mmsi);
			String serviceVesselStatusResponse = getRequest(getServiceVesselStatusGET + "?mmsi=" + mmsi);
			
			// Convert json response to JsonElement
			JsonElement vesselStatusElement = gson.fromJson (vesselStatusResponse, JsonElement.class);
			JsonElement serviceVesselDetailElement = gson.fromJson (serviceVesselDetailResponse, JsonElement.class);
			JsonElement serviceVesselStatusElement = gson.fromJson (serviceVesselStatusResponse, JsonElement.class);
			// Convert JsonElement to JsonObject
			JsonObject vesselStatusJsonObj = vesselStatusElement.getAsJsonObject();
			JsonObject serviceVesselDetailJsonObj = serviceVesselDetailElement.getAsJsonObject();
			JsonObject serviceVesselStatusJsonObj = serviceVesselStatusElement.getAsJsonObject();
			// Get the content of mmsi and Convert it to JsonObject
			JsonObject vesselStatus = vesselStatusJsonObj.getAsJsonObject("Result").getAsJsonObject(mmsi);
			JsonObject serviceVesselDetail = serviceVesselDetailJsonObj.getAsJsonObject("Result").getAsJsonObject(mmsi);
			JsonObject serviceVesselStatus = serviceVesselStatusJsonObj.getAsJsonObject("Result").getAsJsonObject(mmsi);
			
			String LocationName = vesselStatus.get("LocName").getAsString();
			double ETA = vesselStatus.get("ETA").getAsDouble();
			String Destination = vesselStatus.get("Destination").getAsString();
			String Status = vesselStatus.get("Status").getAsString();
			
			int Capacity = serviceVesselDetail.get("Capacity").getAsInt();
			int FlowRate = serviceVesselDetail.get("FlowRate").getAsInt();
			
			int CurrentHold = serviceVesselStatus.get("CurrentHold").getAsInt();
			String PumpStatus = serviceVesselStatus.get("Status").getAsString();
			
			ServiceCraft serviceCraft = new ServiceCraft(mmsi, LocationName, ETA, Destination, 
					Status, Capacity, FlowRate, CurrentHold, PumpStatus);
			serviceCraftList.add(serviceCraft);
			//System.out.println(serviceCraft.toString());
		}
		
		//System.out.println(serviceCraftList.size());
		
		return serviceCraftList;
	}
	
	// Convert current time json to current time string
	public static int travelTimeJsonToInt(Gson gson, String src, String dest, String mmsi) {
		String getTravelTimeGET = "getTravelTime" + "?src=" + src + "&dst=" + dest + "&vsl=" + mmsi;
		String travelTimeResponse = getRequest(getTravelTimeGET);
		
		// Convert json response to JsonElement
		JsonElement element = gson.fromJson (travelTimeResponse, JsonElement.class);
		// Convert JsonElement to JsonObject
		JsonObject jsonObj = element.getAsJsonObject();
		// Get the content of Result and Convert it to JsonObject
		JsonObject travelTimeObj = jsonObj.getAsJsonObject("Result");
		
		int travelTime = travelTimeObj.get("TravelTime").getAsInt();
		
		return travelTime;
	} 
	
	// Convert current time json to current time string
	public static int ETAJsonToInt(Gson gson, String mmsi) {
		String getVesselStatusGET = "getVesselStatus" + "&mmsi=" + mmsi;
		String vesselStatusResponse = getRequest(getVesselStatusGET);
		
		// Convert json response to JsonElement
		JsonElement element = gson.fromJson (vesselStatusResponse, JsonElement.class);
		// Convert JsonElement to JsonObject
		JsonObject jsonObj = element.getAsJsonObject();
		// Get the content of Result and Convert it to JsonObject
		JsonObject MMSIObj = jsonObj.getAsJsonObject("Result").getAsJsonObject(mmsi);
		
		int ETA = MMSIObj.get("ETA").getAsInt();
		
		return ETA;
	} 
	
	public static double[][][] getTotalTravelTimeByVessel(Gson gson, ArrayList<ServiceCraft> serviceCraftList, 
			List<ServiceRequest> serviceRequestList) {
		double[][][] totalTravelTime = new double[serviceRequestList.size()][serviceRequestList.size()][serviceCraftList.size()];
		
		for (int i = 0; i < totalTravelTime.length; i++) {
			for (int j = 0; j < totalTravelTime[i].length; j++) {
				for(int k = 0; k < totalTravelTime[i][j].length; k++) {
					/*if(i == 0 && j != 0) {
						ServiceRequest sr = serviceRequestList.get(j);
						ServiceCraft sc = serviceCraftList.get(k);
						int travelTime = travelTimeJsonToInt(gson, sc.getLocationName(), sr.getLocName(), sc.getMMSI());
						double serviceTime = sc.getServiceTime(sr);
						totalServiceTimeArr[i][j][k] = travelTime + serviceTime;
					}	else if(i != j && j != 0) {
						ServiceRequest srFrom = serviceRequestList.get(i);
						ServiceRequest srTo = serviceRequestList.get(j);
						ServiceCraft sc = serviceCraftList.get(k);
						int travelTime = travelTimeJsonToInt(gson, srFrom.getLocName(), srTo.getLocName(), sc.getMMSI());
						double serviceTime = sc.getServiceTime(srTo);
						totalServiceTimeArr[i][j][k] = travelTime + serviceTime;
					}	else {
						totalServiceTimeArr[i][j][k] = Double.MAX_VALUE;
					}
					System.out.println("i: " + i + " j: " + j + " k: " + k);
					System.out.println(totalServiceTimeArr[i][j][k]);*/
					if(i != j) {
						ServiceRequest srFrom = serviceRequestList.get(i);
						ServiceRequest srTo = serviceRequestList.get(j);
						ServiceCraft sc = serviceCraftList.get(k);
						int travelTime = travelTimeJsonToInt(gson, srFrom.getLocName(), srTo.getLocName(), sc.getMMSI());
						totalTravelTime[i][j][k] = travelTime;
						//double serviceTime = sc.getServiceTime(srTo);
						//totalServiceTimeArr[i][j][k] = travelTime + serviceTime;
						System.out.println("i: " + i + " j: " + j + " k: " + k);
						System.out.println(totalTravelTime[i][j][k]);
					}
				}
			}
		}
		
		
		
		return totalTravelTime;
	}
	
	// Solve Model
	public static ArrayList<Assignment> solveModel(Gson gson, double[][][] totalTravelTimeByVessel, ArrayList<ServiceCraft>serviceCraftList, 
			List<ServiceRequest> serviceRequestList, ArrayList<Location> locationList, LocalDateTime currentDateTime) {
		
		ArrayList<Assignment> assignmentList = new ArrayList<>();
		
		int serviceCraftSize = serviceCraftList.size();
		int serviceRequestSize = serviceRequestList.size();
		int locationSize = locationList.size();
		int requestsAndDepotsAndAnchoragesSize = serviceRequestSize + locationSize;
		
		long currentMilliSeconds = Timestamp.valueOf(currentDateTime).getTime();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		
		try {
			// Creation of empty model
			IloCplex model = new IloCplex();
			
			// Creating the decision Variables (in integer)
			IloIntVar[][][] dvars = new IloIntVar[serviceRequestSize]
												 [serviceRequestSize]
												 [serviceCraftSize];
			
			// Set limit of dvars (0,1)
			for (int i = 0; i < serviceRequestSize; i++) {
				for (int j = 0; j < serviceRequestSize; j++) {
					for(int k = 0; k < serviceCraftSize; k++) {
						dvars[i][j][k] = model.intVar(0,1);
					}
				}
			}
			
			//(Linear Integer Expression)
			IloLinearIntExpr objFunction = model.linearIntExpr();
			
			// Set objective function dvars
			for (int i = 0; i < serviceRequestSize; i++) {
				for (int j = 0; j < serviceRequestSize; j++) {
					for(int k = 0; k < serviceCraftSize; k++) {
						// (Total Travel Time) * dvar[i][j][k]
						objFunction.addTerm((int) totalTravelTimeByVessel[i][j][k], dvars[i][j][k]);
					}
				}
			}
			model.addMinimize(objFunction);
			
			// Add constraints
			List<IloRange> constraints = new ArrayList<>();
			
			// C1 - Sum of xijk for every j can be more than and equals to 0 (multiple sc serving 1 request)
			for(int j = 0; j < serviceRequestSize; j++) {
				IloLinearIntExpr constraint = model.linearIntExpr();
				for(int i = 0; i < serviceRequestSize; i++) {
					for(int k = 0; k < serviceCraftSize; k++) {
						if(i!=j) {
							constraint.addTerm(1, dvars[i][j][k]);
						}
					}
				}
				constraints.add(model.addGe(constraint, 1, "c1"));
			}
			
			// C2 - For each of xijk, range is from 0 to 1
			for(int i = 0; i < serviceRequestSize; i++) {
				IloLinearIntExpr constraint = model.linearIntExpr();
				for(int j = 0; j < serviceRequestSize; j++) {
					for(int k = 0; k < serviceCraftSize; k++) {
						constraint.addTerm(1, dvars[i][j][k]);
						constraints.add(model.addRange(0.0, constraint, 1.0, "c2"));
					}
				}
			}
			
			/*
			// C3 - For each of xijk that is 1, the sum of the fuel demand has to be less than service craft k's current hold
			for(int k = 0; k < serviceCraftSize; k++) {
				IloLinearIntExpr constraint = model.linearIntExpr();
				for(int i = 0; i < serviceRequestSize; i++) {
					for(int j = 0; j < serviceRequestSize; j++) {
						constraint.addTerm((int) serviceRequestList.get(j).getFuelRequired(), dvars[i][j][k]);
					}
				}
				constraints.add(model.addLe(constraint, serviceCraftList.get(k).getCurrentHold(), "c3"));
			}
			*/
			
			/*
			double M = 0;
			
			for(int i = 0; i < serviceRequestSize; i++) {
				for(int j = 0; j < serviceRequestSize; j++) {
					for(int k = 0; k < serviceCraftSize; k++) {
						ServiceRequest serviceRequestI = serviceRequestList.get(i);
						long milliSecondsI = 0;
						if(serviceRequestI.getTime() != null) {
							LocalDateTime timeofI = LocalDateTime.parse(serviceRequestI.getTime(), formatter);
							milliSecondsI = Timestamp.valueOf(timeofI).getTime();
						}
						
						ServiceRequest serviceRequestJ = serviceRequestList.get(j);
						long milliSecondsJ = 0;
						if(serviceRequestJ.getTime() != null) {
							LocalDateTime timeofJ = LocalDateTime.parse(serviceRequestJ.getTime(), formatter);
							milliSecondsJ = Timestamp.valueOf(timeofJ).getTime();
						}
						
						ServiceCraft serviceCraftK = serviceCraftList.get(k);
						
						// Time Requested of i + traveltime of ij -  ETA of i
						double val = milliSecondsI + (totalServiceTimeByVessel[i][j][k]*60000) - 
								(ETAJsonToInt(gson, serviceRequestI.getMMSI()) * 60000);
						
						if(M < val) {
							M = val;
						}
					}
				}
			}
			
			// C4 - 
			
			for(int i = 0; i < serviceRequestSize; i++) {
				for(int j = 0; j < serviceRequestSize; j++) {
					for(int k = 0; k < serviceCraftSize; k++) {
						ServiceRequest serviceRequestI = serviceRequestList.get(i);
						long milliSecondsI = 0;
						if(serviceRequestI.getTime() != null) {
							LocalDateTime timeofI = LocalDateTime.parse(serviceRequestI.getTime(), formatter);
							milliSecondsI = Timestamp.valueOf(timeofI).getTime();
						}
						
						ServiceRequest serviceRequestJ = serviceRequestList.get(j);
						long milliSecondsJ = 0;
						if(serviceRequestJ.getTime() != null) {
							LocalDateTime timeofJ = LocalDateTime.parse(serviceRequestJ.getTime(), formatter);
							milliSecondsJ = Timestamp.valueOf(timeofJ).getTime();
						}
						
						ServiceCraft serviceCraftK = serviceCraftList.get(k);
						
						double totalServiceTime = (totalServiceTimeByVessel[i][j][k]*60000);
						IloLinearNumExpr constraint = model.linearNumExpr();
						
						constraint.addTerm( 1.0, milliSecondsI);
						constraint.addTerm(-1.0, milliSecondsJ);
						constraint.addTerm( M  , dvars[i][j][k]);
						constraints.add(model.addLe(constraint, M-totalServiceTime, "c4"));
					}
				}
			}
			*/
			
			/*
			// For each of xijk, time requested at i + total service time(service time + travel time) must be less than time requested at j
			for(int k = 0; k < serviceCraftSize; k++) {
				IloLinearNumExpr constraint = model.linearNumExpr();
				for(int i = 0; i < serviceRequestSize; i++) {
					for(int j = 0; j < serviceRequestSize; j++) {
						ServiceRequest serviceRequestI = serviceRequestList.get(i);
						long milliSecondsI = 0;
						if(serviceRequestI.getTime() != null) {
							LocalDateTime timeofI = LocalDateTime.parse(serviceRequestI.getTime(), formatter);
							milliSecondsI = Timestamp.valueOf(timeofI).getTime();
						}
						
						ServiceRequest serviceRequestJ = serviceRequestList.get(j);
						long milliSecondsJ = 0;
						if(serviceRequestJ.getTime() != null) {
							LocalDateTime timeofJ = LocalDateTime.parse(serviceRequestJ.getTime(), formatter);
							milliSecondsJ = Timestamp.valueOf(timeofJ).getTime();
						}
						
						double totalServiceTimeofIJ = totalServiceTimeByVessel[i][j][k];
						long milliSecondsTotalServiceTimeofIJ = (long) (totalServiceTimeofIJ * 60000);
						
						constraint.addTerm((double) (milliSecondsI + milliSecondsTotalServiceTimeofIJ - milliSecondsJ) , dvars[i][j][k]);
					}
				}
				constraints.add(model.addLe(constraint, 0));
			}*/
			
			// Solve model
			boolean isSolved = model.solve();
			if(isSolved) {
				int objValue = (int)model.getObjValue();
				System.out.println("\nObjective value: " + objValue + " mins");
				for(int k = 0; k < serviceCraftSize; k++) {
					for (int i = 0; i < serviceRequestSize; i++) {
						for (int j = 0; j < serviceRequestSize; j++) {
							int dVarValue = (int)model.getValue(dvars[i][j][k]);
							if(dVarValue != 0) {
								ServiceCraft sc = serviceCraftList.get(k);
								ServiceRequest srFrom = serviceRequestList.get(i);
								ServiceRequest srTo = serviceRequestList.get(j);
								
								//System.out.println(srFrom.toString());
								//System.out.println(srTo.toString());
								// Time requested - totalServiceTime
								
								double serviceTime = sc.getServiceTime(srFrom);
								
								LocalDateTime requestedTime = LocalDateTime.parse(srTo.getTime(), formatter);
								String departTime = requestedTime.minusMinutes((long) (totalTravelTimeByVessel[i][j][k] + serviceTime)).format(formatter);
								
								Assignment assignment = new Assignment(sc.getMMSI(), "Supply", srTo.getRequestID(), departTime, sc.getCapacity());
								assignmentList.add(assignment);
							}
						}
					}
				}
				System.out.println("=================================================");
				System.out.println("Model solved");
			}	else {
				System.out.println("=================================================");
				System.out.println("Model not solved");
			}
		} catch (IloException e) {
			e.printStackTrace();
		}
		
		return assignmentList;
	}
	
}