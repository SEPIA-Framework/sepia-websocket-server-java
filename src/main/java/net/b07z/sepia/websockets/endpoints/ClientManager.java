package net.b07z.sepia.websockets.endpoints;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.server.RequestParameters;
import net.b07z.sepia.server.core.server.RequestPostParameters;
import net.b07z.sepia.server.core.server.SparkJavaFw;
import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.server.core.users.SharedAccessItem;
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketUserPool;
import net.b07z.sepia.websockets.server.Statistics;
import spark.Request;
import spark.Response;

/**
 * Get information about connected clients or trigger refresh etc..
 * 
 * @author Florian Quirin
 */
public class ClientManager {
	
	/**
     * As superuser: Get all clients connected to the server (session = open).<br>
     * As simple user: Get all own clients (same userId) and shared access clients (via 'includeShared').
     */
    public static String getClientConnections(Request request, Response response){
    	//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			long tic = Debugger.tic();
			
			//depends on user
			boolean getAll = userAccount.hasRole(Role.superuser.name());
			String userId = userAccount.getUserID();
			try{
				//get clients
				Collection<SocketUser> users = SocketUserPool.getAllUsers();
				JSONArray result = new JSONArray();

				//check users
				if (Is.notNullOrEmpty(users)){
					if (getAll){
						//Return all
						for (SocketUser u : users){
							//check session
							Session s =	u.getUserSession();
							if (s != null && s.isOpen()){
								JSON.add(result, u.getUserListEntry());
							}
						}
					}else{
						//Return own and optionally sessions with shared permissions
						boolean includeShared = params.getBoolOrDefault("includeShared", false);
						Map<String, Boolean> userAlreadyChecked = new HashMap<>();
						Map<String, SharedAccessItem> acceptOnNextCheck = new HashMap<>();
						for (SocketUser u : users){
							//check session
							Session s =	u.getUserSession();
							if (s == null || !s.isOpen()){
								continue;
							}
							//Own user
							if (userId.equals(u.getUserId())){
								//check session
								JSON.add(result, u.getUserListEntry());
								
							//User that was already checked for shared-access?
							}else if (userAlreadyChecked.getOrDefault(u.getUserId(), false)){
								//TODO: this is a random version of the 'sharedAccessItem' list of this user ...
								//... only if all users reconnect after a change it is "fresh" and identical for all sessions of this user
								//accept?
								if (acceptOnNextCheck.containsKey(u.getUserId())){
									addSharedClientData(u, acceptOnNextCheck.get(u.getUserId()), result);
								}else if (acceptOnNextCheck.containsKey(u.getUserId() + "_" + u.getDeviceId())){
									addSharedClientData(u, acceptOnNextCheck.get(u.getUserId() + "_" + u.getDeviceId()), result);
								}
							//User with potentially shared access (that haven't been checked yet)?
							}else if (includeShared && u.getSharedAccess() != null){
								//iterate through all shared permissions and find matching user-permission (every SocketUser has the whole set!)
								//TODO: when client submits finer filter we can check a specific key (dataType) via params.getJsonArray("includeSharedFor");
								u.getSharedAccess().entrySet().forEach(saiEntry -> {
									//saiEntry.getKey() -> e.g. key='remoteActions'
									saiEntry.getValue().forEach(sharedAccessItem -> {
										//does this 'sharedAccessItem' include our user?
										if (sharedAccessItem.getUser().equals(userId)){
											//check device restrictions (null or match)
											boolean userAllowsAllDevices = Is.nullOrEmpty(sharedAccessItem.getDevice());
											if (userAllowsAllDevices){
												//accept now and remember that all are allowed
												addSharedClientData(u, sharedAccessItem, result); 
												acceptOnNextCheck.put(u.getUserId(), sharedAccessItem);
											}else if (u.getDeviceId().equals(sharedAccessItem.getDevice())){
												//accept now and remember that we accept this user device
												addSharedClientData(u, sharedAccessItem, result);
												acceptOnNextCheck.put(u.getUserId() + "_" + sharedAccessItem.getDevice(), sharedAccessItem);
											}else{
												//only remember that we will accept this user device
												acceptOnNextCheck.put(u.getUserId() + "_" + sharedAccessItem.getDevice(), sharedAccessItem);
											}
										}
									});
								});
								//make search more effective - see notes above: this is only same for all user sessions if all users reconnect after changes!
								userAlreadyChecked.put(u.getUserId(), true);
							}
						}
					}
				}
				
				//result
				JSONObject msgJSON = JSON.make(
						"result", "success",	
						"clients", result
				);
				
				//statistics
				Statistics.addOtherApiHit("getClientConnections");
				Statistics.addOtherApiTime("getClientConnections", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				
			}catch (Exception e){
				//error
				JSONObject msgJSON = JSON.make("result", "fail", "error", e.getMessage());
				
				//statistics
				Statistics.addOtherApiHit("getClientConnections-fail");
				Statistics.addOtherApiTime("getClientConnections-fail", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
    }
    //add client info for shared access
    private static void addSharedClientData(SocketUser u, SharedAccessItem sharedAccessItem, JSONArray resultSoFar){
    	JSONObject userJsonReduced = u.getReducedListEntry();
		JSON.put(userJsonReduced, "isShared", true);
		JSON.put(userJsonReduced, "sharedAccessInfo", sharedAccessItem.toJson());
		JSON.add(resultSoFar, userJsonReduced);
    }
    
    /**
	 * Refresh client connections by provoking a reconnect.
	 */
	public static String refreshClientConnections(Request request, Response response){
		//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			long tic = Debugger.tic();
			
			boolean isAdmin = userAccount.hasRole(Role.superuser.name());
			String userId = userAccount.getUserID();
			
			try{
				//get clients
				Collection<SocketUser> users = SocketUserPool.getAllUsers();
				
				//currently we support "all" and "own" - "all" requires admin
				boolean refreshAll = params.getBoolOrDefault("all", false);
				if (refreshAll && !isAdmin){
					//statistics
					Statistics.addOtherApiHit("refreshClientConnections-fail");
					Statistics.addOtherApiTime("refreshClientConnections-fail", tic);
					//refuse
					JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
				}
				
				//check users
				JSONArray result = new JSONArray();
				if (Is.notNullOrEmpty(users)){
					for (SocketUser u : users){
						//all or own
						if (refreshAll || u.getUserId().equals(userId)){
							u.getUserSession().close(StatusCode.SERVICE_RESTART, "reconnect required to update data");
							u.setClosing(); 	//mark as "closing" to prevent further messages
							JSON.add(result, JSON.make("userId", u.getUserId(), "deviceId", u.getDeviceId()));
						}
					}
				}
				
				//result
				JSONObject msgJSON = JSON.make(
						"result", "success",	
						"sessionsClosed", result
				);
				
				//statistics
				Statistics.addOtherApiHit("refreshClientConnections");
				Statistics.addOtherApiTime("refreshClientConnections", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				
			}catch (Exception e){
				//error
				JSONObject msgJSON = JSON.make("result", "fail", "error", e.getMessage());
				
				//statistics
				Statistics.addOtherApiHit("refreshClientConnections-fail");
				Statistics.addOtherApiTime("refreshClientConnections-fail", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
	}
}
