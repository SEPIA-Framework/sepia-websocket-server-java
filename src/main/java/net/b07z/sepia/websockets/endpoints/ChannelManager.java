package net.b07z.sepia.websockets.endpoints;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.server.RequestParameters;
import net.b07z.sepia.server.core.server.RequestPostParameters;
import net.b07z.sepia.server.core.server.SparkJavaFw;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketChannelPool;
import spark.Request;
import spark.Response;

/**
 * Create, join and remove channels.
 * 
 * @author Florian Quirin
 */
public class ChannelManager {
	
	/**
     * Create a new channel.
     */
    public static String createChannel(Request request, Response response){
    	//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			//log.info("Authenticated: " + userAccount.getUserID() + ", roles: " + userAccount.getUserRoles()); 		//debug
			if (userAccount.hasRole(Role.developer.name())){
				//get parameters
				String channelId = params.getString("channelId");
				JSONArray initialMembers = params.getJsonArray("members");
				String owner = userAccount.getUserID();
				boolean isPublic = params.getBoolOrDefault("isPublic", false);
				boolean addAssistant = params.getBoolOrDefault("addAssistant", true);
				
				if (channelId == null || channelId.isEmpty()){
					JSONObject msgJSON = JSON.make("result", "fail", "error", "missing channelId");
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}else{
					//filter system channels (user can't create them)
					if (SocketChannel.systemChannels.contains(channelId)){
						JSONObject msgJSON = JSON.make("result", "fail", "error", "invalid channelId");
						return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
					}
					//filter valid channel names
					String cleanChannelId = channelId.replaceAll("[^\\w\\s]","").replaceAll("\\s+", " ");
					if (!cleanChannelId.equals(channelId)){
						JSONObject msgJSON = JSON.make("result", "fail", "error", "invalid channelId, please use only letters, numbers and '_'.");
						return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
					}
				}
				try{
					//create channel
					String channelKey = SocketChannelPool.createChannel(channelId, owner, isPublic);
					if (channelKey != null){
						List<String> members = new ArrayList<>();
						members.add(owner);
						if (initialMembers != null && !initialMembers.isEmpty()){
							for (Object o : initialMembers){
								members.add((String) o);
							}
						}
						SocketChannel sc = SocketChannelPool.getChannel(channelId);
						for (String s : members){
							sc.addUser(s, channelKey);
							//System.out.println("Member: " + s); 									//DEBUG
						}
						if (addAssistant){
							sc.addSystemDefaultAssistant(); 	//Add SEPIA too
							//System.out.println("Member: " + SocketConfig.systemAssistantId); 		//DEBUG
						}
						JSONObject msgJSON = JSON.make("result", "success",	"key", channelKey, "channelId", channelId);
						return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
					
					}else{
						//error
						JSONObject msgJSON = JSON.make("result", "fail", "error", "creation failed! Maybe already exists?");
						return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
					}
				}catch (Exception e){
					//error
					JSONObject msgJSON = JSON.make("result", "fail", "error", e.getMessage());
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}	
			}else{
				//refuse
				JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized, missing required role");
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 404);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 404);
		}
    }
    
    /**
     * Join a channel.
     */
    public static String joinChannel(Request request, Response response){
    	//TODO:
    	// - authenticate
    	// - get parameters (channelId, key, user)
    	// - return success
    	// ...
    	
    	//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
    	
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not yet implemented");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 404);
		}
    }
}
