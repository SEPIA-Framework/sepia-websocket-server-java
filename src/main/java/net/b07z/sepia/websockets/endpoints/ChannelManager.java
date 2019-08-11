package net.b07z.sepia.websockets.endpoints;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.server.RequestParameters;
import net.b07z.sepia.server.core.server.RequestPostParameters;
import net.b07z.sepia.server.core.server.SparkJavaFw;
import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketChannelPool;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.database.ChannelsDatabase;
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
			if (userAccount.hasRole(Role.user.name())){
				//get parameters
				String channelName = params.getString("channelName");
				if (Is.nullOrEmpty(channelName)){
					channelName = "New Channel";
				}else{
					channelName = channelName.replaceAll("[^\\w\\s]","").replaceAll("\\s+", " ");
				}
				JSONArray initialMembers = params.getJsonArray("members");
				String ownerId = userAccount.getUserID();
				boolean isPublic = params.getBoolOrDefault("isPublic", false);
				boolean addAssistant = params.getBoolOrDefault("addAssistant", true);
				
				//build channel
				try{
					//check number of allowed own channels
					ChannelsDatabase channelsDb = SocketConfig.getDefaultChannelsDatabase();
					if (!userAccount.hasRole(Role.superuser.name())){
						List<SocketChannel> socketChannelsOfUser = channelsDb.getAllChannelsOwnedBy(ownerId);
						if (socketChannelsOfUser == null){
							JSONObject msgJSON = JSON.make("result", "fail", "error", "failed to check channels, please try again or check database connection.");
							return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
						}else if (socketChannelsOfUser.size() >= SocketConfig.maxChannelsPerUser){
							JSONObject msgJSON = JSON.make("result", "fail", "error", "user has reached maximum number of allowed channels: " + socketChannelsOfUser.size() + " of " + SocketConfig.maxChannelsPerUser);
							return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
						}
					}
					
					//get new ID for channel
					String channelId = SocketChannelPool.getRandomUniqueChannelId();

					//create channel
					SocketChannel sc = SocketChannelPool.createChannel(channelId, ownerId, isPublic, channelName);
					if (sc != null){
						String channelKey = sc.getChannelKey();
						Set<String> members = new HashSet<>();
						members.add(ownerId);
						if (initialMembers != null && !initialMembers.isEmpty()){
							for (Object o : initialMembers){
								members.add((String) o);
							}
						}
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
						
						//TODO: store channel
					
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
     * Delete existing channel.
     */
    public static String deleteChannel(Request request, Response response){
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
			
			//filter system channels (user can't create them) - TODO: move this where it makes sense
			/*
			if (SocketChannel.systemChannels.contains(channelId)){
				JSONObject msgJSON = JSON.make("result", "fail", "error", "invalid channelId");
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}
			*/
			
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not yet implemented");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			
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
