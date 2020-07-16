package net.b07z.sepia.websockets.endpoints;

import java.util.Collection;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.server.RequestParameters;
import net.b07z.sepia.server.core.server.RequestPostParameters;
import net.b07z.sepia.server.core.server.SparkJavaFw;
import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketUserPool;
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
							for (SocketUser u : users){
								if (userId.equals(u.getUserId())){
									//check session
									Session s =	u.getUserSession();
									if (s != null && s.isOpen()){
										JSON.add(result, u.getUserListEntry());
									}
								}
							}
						}
					}
					
					//result
					JSONObject msgJSON = JSON.make(
							"result", "success",	
							"clients", result
					);
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
					
				}catch (Exception e){
					//error
					JSONObject msgJSON = JSON.make("result", "fail", "error", e.getMessage());
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
