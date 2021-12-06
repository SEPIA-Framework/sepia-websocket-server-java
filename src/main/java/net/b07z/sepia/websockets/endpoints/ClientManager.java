package net.b07z.sepia.websockets.endpoints;

import java.util.Collection;

import org.eclipse.jetty.websocket.api.Session;
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
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketUserPool;
import net.b07z.sepia.websockets.server.Statistics;
import spark.Request;
import spark.Response;

/**
 * Get information about connected clients.
 * 
 * @author Florian Quirin
 */
public class ClientManager {
	
	private static boolean allowBasicUserStats = true;		//allow basic users to get info about own clients? - TODO: add config option
	
	/**
     * As superuser: Get all clients connected to the server (session = open).<br>
     * As simple user: Get all own clients (same userId).
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
			if (getAll || allowBasicUserStats){
				try{
					//get clients
					Collection<SocketUser> users = SocketUserPool.getAllUsers();
					JSONArray result = new JSONArray();

					//check users
					if (Is.notNullOrEmpty(users)){
						if (getAll){
							//All
							for (SocketUser u : users){
								//check session
								Session s =	u.getUserSession();
								if (s != null && s.isOpen()){
									JSON.add(result, u.getUserListEntry());
								}
							}
						}else{
							//Single user
							boolean includeShared = params.getBoolOrDefault("includeShared", false);
							//TODO: use more detailed filter: params.getBoolOrDefault("includeSharedFor", false);
							for (SocketUser u : users){
								if (userId.equals(u.getUserId())){
									//check session
									Session s =	u.getUserSession();
									if (s != null && s.isOpen()){
										JSON.add(result, u.getUserListEntry());
									}
								}else if (includeShared && u.getSharedAccess() != null){
									//iterate through all shared permissions and find matching user
									//NOTE: with finer filter we can use specific key (dataType) instead ...
									u.getSharedAccess().entrySet().forEach(es -> {
										es.getValue().forEach(sharedAccessItem -> {
											//user we are looking for?
											if (sharedAccessItem.getUser().equals(userId)){
												//TODO: filter device and details?
												JSONObject userJsonReduced = u.getReducedListEntry();
												JSON.put(userJsonReduced, "isShared", true);
												JSON.put(userJsonReduced, "sharedAccessInfo", sharedAccessItem.toJson());
												JSON.add(result, userJsonReduced);
											}
										});
									});
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
				JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized, missing required role (restricted access)");
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
    }
}
