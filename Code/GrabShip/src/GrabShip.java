import ilog.concert.*;
import ilog.cplex.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.*;
import org.apache.http.impl.client.*;
import org.apache.http.util.*;
import com.google.gson.*;

/**
 *
 * @author  Team GrabShip
 */

public class GrabShip {
	// Change this variable to set the server location including the port number
	private static final String SERVER_LOCATION = "http://127.0.0.1:8080/";
	// Change this variable to set the batching interval (in hours)
	private static final int INTERVAL = 3;
	
	
	// This is to indicate the user agent(for calling GET/POST request)
	private static final String USER_AGENT = "application/json";
	// This is to indicate the last request ID that has been assigned by the model
	private static int lastRequestID = 0;
	
	public static void main(String[] args) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		// Get Location List
		String locGET = "getLocationList";
		String locResponse = getRequest(locGET);
		ArrayList<Location> locationList = locationJsonToLocationList(gson, locResponse);
		
		// Get all depot and home anchorage
		ArrayList<Location> depotList = new ArrayList<>();
		ArrayList<Location> homeAnchorageList = new ArrayList<>();
		
		for(Location loc : locationList) {
			if(loc.getLocationType().equalsIgnoreCase("Depot")) {
				depotList.add(loc);
			}
			if(loc.getLocationType().equalsIgnoreCase("Anchorage") && loc.isBunkerHome()) {
				homeAnchorageList.add(loc);
			}
		}
		
		try {
			// Infinite loop
			while(true) {
				// Get current time in simulation
				String currentTimeGET = "getCurrentTime";
				String currentTimeResponse = getRequest(currentTimeGET);
				String currentTime = currentTimeJsonToString(gson, currentTimeResponse);
				
				System.out.println("Current time: " + currentTime);
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
				LocalDateTime currentDateTime = LocalDateTime.parse(currentTime, formatter);
				
				
				// Run the model for an interval set by the user
				if(	currentDateTime.getHour() % INTERVAL == 0) {
					// Get ServiceRequest List
					String serviceRequestGET = "getServiceDetail";
					String serviceRequestResponse = getRequest(serviceRequestGET);
					List<ServiceRequest> serviceRequestList = serviceRequestJsonToServiceRequestList(gson, serviceRequestResponse);
					for(ServiceRequest sr : serviceRequestList) {
						System.out.println(sr.toString());
					}
					
					// Remove all previously assigned service requests
					for(int i = lastRequestID-1 ; i >= 0; i--) {
						serviceRequestList.remove(i);
					}
					
					// Get all Service Crafts
					String serviceCraftGET = "getServiceVessel";
					String serviceCraftResponse = getRequest(serviceCraftGET);
					ArrayList<ServiceCraft> serviceCraftList = serviceCraftJsonToServiceCraftList(gson, serviceCraftResponse);
					
					
					double[][] totalServiceTimeArr = getTotalTravelTimeByVessel(gson, serviceCraftList, serviceRequestList);
					
					
					List<Assignment> assignmentList =  solveModel(gson, totalServiceTimeArr, serviceCraftList, 
							serviceRequestList, depotList, homeAnchorageList);
					
					// Sort by depart time and output all assignments
					/*
					Collections.sort(assignmentList);
					for(Assignment a : assignmentList) {
						System.out.println(a.toString());
					}
					System.out.println("\nAssignment size: " + assignmentList.size());
					*/
					
					// Send POST request
					String response = postRequest(gson, "sendJobs", assignmentList);
					System.out.println("\nResponse: \n" + response);
					
					// Clear all dynamic lists
					serviceRequestList.clear();
					serviceCraftList.clear();
					
					// Remove all assignments
					//assignmentList.clear();
				}
				
				// After each iteration, sleep for 5 seconds
				Thread.sleep(5 * 1000);
			}
		}	catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	// For calling GET requests
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
        }
		return result.toString();
	}
	
	// For sending POST request
	public static String postRequest(Gson gson, String POST, List<Assignment> assignmentList) {
		// Convert assignment list to JSON
		// Create assignment JsonArray
		JsonArray assignmentArray = new JsonArray();
		for(Assignment assignment : assignmentList) {
			assignmentArray.add(assignment.toJSON());
		}
		// Create the outer layer then add array into JsonObject
		JsonObject outerLayer = new JsonObject();
		outerLayer.add("Assign", assignmentArray);
		// Convert JsonObject to string
		String assignmentJSON = gson.toJson(outerLayer);
		
		System.out.println("\nPOST JSON: \n" + assignmentJSON);
		
		// Send POST request
		HttpClient httpClient = HttpClientBuilder.create().build();
		String responseString = "No response from server";
		try {
			String url = SERVER_LOCATION + POST;
		    HttpPost request = new HttpPost(url);
		    StringEntity params =new StringEntity(assignmentJSON);
		    request.addHeader("content-type", "application/json");
		    request.setEntity(params);
		    HttpResponse response = httpClient.execute(request);
		    JsonObject json = gson.fromJson(EntityUtils.toString(response.getEntity()), JsonElement.class).getAsJsonObject();
		    responseString = gson.toJson(json);
		    
		}	catch (Exception e) {
			e.printStackTrace();
		}
		return responseString;
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
			int bkCapacity = 2;
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
		String getVesselStatusGET = "getVesselStatus" + "?mmsi=" + mmsi;
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
	
	// Get a 2d array containing the travel time to a service request by a service craft
	public static double[][] getTotalTravelTimeByVessel(Gson gson, ArrayList<ServiceCraft> serviceCraftList, 
			List<ServiceRequest> serviceRequestList) {
		double[][] totalTravelTime = new double[serviceCraftList.size()][serviceRequestList.size()];
		
		for (int i = 0; i < serviceCraftList.size(); i++) {
			for (int j = 0; j < serviceRequestList.size(); j++) {
				ServiceCraft sc = serviceCraftList.get(i);
				ServiceRequest srTo = serviceRequestList.get(j);
				
				int travelTime = travelTimeJsonToInt(gson, sc.getLocationName(), srTo.getLocName(), sc.getMMSI());
				totalTravelTime[i][j] = travelTime;
				
				//System.out.println("i: " + i + " j: " + j);
				//System.out.println(totalTravelTime[i][j]);
			}
		}
		
		return totalTravelTime;
	}
	
	// Solve Model
	public static ArrayList<Assignment> solveModel(Gson gson, double[][] totalTravelTimeByVessel, ArrayList<ServiceCraft>serviceCraftList, 
			List<ServiceRequest> serviceRequestList, ArrayList<Location> depotList, ArrayList<Location> homeAnchorageList) {
		ArrayList<Assignment> assignmentList = new ArrayList<>();
		
		int serviceCraftSize = serviceCraftList.size();
		int serviceRequestSize = serviceRequestList.size();
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		
		// Sort service request by time requested
		//Collections.sort(serviceRequestList);
		
		try {
			// Creation of empty model
			IloCplex model = new IloCplex();
			
			// Creating the decision Variables (in integer)
			IloIntVar[][] dvars = new IloIntVar[serviceCraftSize][serviceRequestSize];
			
			// Set limit of dvars (0,1)
			for (int i = 0; i < serviceCraftSize; i++) {
				for (int j = 0; j < serviceRequestSize; j++) {
					dvars[i][j] = model.intVar(0,1);
				}
			}
			
			// (Linear Integer Expression)
			IloLinearIntExpr objFunction = model.linearIntExpr();
			
			// Set objective function dvars
			for (int i = 0; i < serviceCraftSize; i++) {
				for (int j = 0; j < serviceRequestSize; j++) {
					// (Total Travel Time) * dvar[i][j]
					objFunction.addTerm((int) totalTravelTimeByVessel[i][j], dvars[i][j]);
				}
			}
			model.addMinimize(objFunction);
			
			// Add constraints
			List<IloRange> constraints = new ArrayList<>();
			
			// C1 - Sum of xij for every j has to be greater than 1
			for(int j = 0; j < serviceRequestSize; j++) {
				IloLinearIntExpr constraint = model.linearIntExpr();
				for(int i = 0; i < serviceCraftSize; i++) {
					constraint.addTerm(1, dvars[i][j]);
				}
				constraints.add(model.addGe(constraint, 1, "C1"));
			}
			
			
			// C2 - Sum of all service craft's current hold for every assigned ServiceRequest must be more than the 
			// ServiceRequest's fuel requested
			for(int j = 0; j < serviceRequestSize; j++) {
				IloLinearIntExpr constraint = model.linearIntExpr();
				for(int i = 0; i < serviceCraftSize; i++) {
					constraint.addTerm(serviceCraftList.get(i).getCurrentHold(), dvars[i][j]);
				}
				constraints.add(model.addGe(constraint, serviceRequestList.get(j).getFuelRequired(), "C2"));
			}
			
			// C3 - For each of xij, range is from 0 to 1
			for(int i = 0; i < serviceCraftSize; i++) {
				IloLinearIntExpr constraint = model.linearIntExpr();
				for(int j = 0; j < serviceRequestSize; j++) {
					constraint.addTerm(1, dvars[i][j]);
					constraints.add(model.addRange(0.0, constraint, 1.0, "C3"));
				}
			}
			
			
			// Solve model
			boolean isSolved = model.solve();
			if(isSolved) {
				int objValue = (int)model.getObjValue();
				System.out.println("\nObjective value (Travelling time by all assigned SC) : " + objValue + " mins");
				for (int i = 0; i < serviceCraftSize; i++) {
					for (int j = 0; j < serviceRequestSize; j++) {
						int dVarValue = (int)model.getValue(dvars[i][j]);
						// If assignment is found
						if(dVarValue != 0) {
							ServiceCraft sc = serviceCraftList.get(i);
							ServiceRequest srTo = serviceRequestList.get(j);
							
							// Depart Time = Current time + ETA - total travel time
							String currentTimeGET = "getCurrentTime";
							String currentTimeResponse = getRequest(currentTimeGET);
							String currentTime = currentTimeJsonToString(gson, currentTimeResponse);
							
							LocalDateTime currentDateTime = LocalDateTime.parse(currentTime, formatter);
							int eta = ETAJsonToInt(gson, srTo.getMMSI());
							
							// Add 30 mins buffer (in case depart time < current time)
							LocalDateTime etaTime = currentDateTime.plusMinutes((long) (eta + 30));
							LocalDateTime departTime = etaTime.minusMinutes((long) (totalTravelTimeByVessel[i][j]));
							String departTimeString = departTime.format(formatter);
							
							
							Assignment assignment = new Assignment(sc.getMMSI(), "Supply", srTo.getRequestID(), departTimeString, sc.getCurrentHold());
							assignmentList.add(assignment);
							
							
							// Now feed in home anchorage + depot in the assignment
							
							
							// Assign SC to depot
							// get service time of service request
							double serviceTime = sc.getServiceTime(srTo);
							// get depart time to depot (depart time of previous assignment + 
							// travel time to the request + service time of the request)
							LocalDateTime departTimeToDepot = departTime.plusMinutes((long) (totalTravelTimeByVessel[i][j]))
									.plusMinutes((long) serviceTime);
							String departTimeToDepotString = departTimeToDepot.format(formatter);
							// get closest depot
							Location chosenDepot = depotList.get(0);
							double chosenTravelTimeToDepot = travelTimeJsonToInt(gson, srTo.getLocName(), chosenDepot.getLocationName(), sc.getMMSI());
							
							for(int k = 1; k < depotList.size(); k++) {
								double travelTimeToDepot = travelTimeJsonToInt(gson, srTo.getLocName(), depotList.get(k).getLocationName(), sc.getMMSI());
								if(travelTimeToDepot <= chosenTravelTimeToDepot) {
									chosenTravelTimeToDepot = travelTimeToDepot;
									chosenDepot = depotList.get(k);
								}
							}
							// Resupply to full amount given that there is still remaining fuel
							int resupplyAmount = sc.getCapacity();
							Assignment depotAssignment = new Assignment(sc.getMMSI(), "Resupply", chosenDepot.getLocationName(), 
									departTimeToDepotString, resupplyAmount);
							assignmentList.add(depotAssignment);
							
							
							// Assign SC to Home Anchorage
							// get service time of depot
							double resupplyTime = resupplyAmount / sc.getFlowRate();
							// get depart time to home anchorage (depart time of request to depot + 
							// travel time of request to depot + depot service time)
							LocalDateTime departTimeToHomeAnchorage = departTimeToDepot.plusMinutes((long) chosenTravelTimeToDepot)
									.plusMinutes((long) resupplyTime);
							String departTimeToHomeAnchorageString = departTimeToHomeAnchorage.format(formatter);
							// get closest home anchorage
							Location chosenHomeAnchorage = homeAnchorageList.get(0);
							double chosenTravelTimeToHomeAnchorage = travelTimeJsonToInt(gson, chosenDepot.getLocationName(), chosenHomeAnchorage.getLocationName(), sc.getMMSI());
							
							for(int k = 1; k < homeAnchorageList.size(); k++) {
								double travelTimeToHomeAnchorage = travelTimeJsonToInt(gson, chosenDepot.getLocationName(), homeAnchorageList.get(k).getLocationName(), sc.getMMSI());
								if(travelTimeToHomeAnchorage <= chosenTravelTimeToHomeAnchorage) {
									chosenTravelTimeToHomeAnchorage = travelTimeToHomeAnchorage;
									chosenHomeAnchorage = homeAnchorageList.get(k);
								}
							}
							Assignment homeAnchorageAssignment = new Assignment(sc.getMMSI(), "Idle", chosenHomeAnchorage.getLocationName(), 
									departTimeToHomeAnchorageString, 0);
							assignmentList.add(homeAnchorageAssignment);
						}
					}
				}
				
				System.out.println("=================================================");
				System.out.println("Model solved");
				// Set last request ID to ensure that previously assigned service requests will not be assigned again
				lastRequestID += serviceRequestSize;
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