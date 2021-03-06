package net.b07z.sepia.websockets.endpoints;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.server.SocketChannelHistory;
import net.b07z.sepia.websockets.server.SocketChannelPool;
import net.b07z.sepia.websockets.server.Statistics;
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
			long tic = Debugger.tic();
			
			//log.info("Authenticated: " + userAccount.getUserID() + ", roles: " + userAccount.getUserRoles()); 		//debug
			if (userAccount.hasRole(Role.user.name())){
				//get parameters
				String channelName = params.getString("channelName");
				if (!Is.nullOrEmpty(channelName)){
					//make sure there are no weird characters in the name (consider Converters.escapeHTML ?)
					channelName = channelName.replaceAll("[^\\w\\s#\\-\\+&\\?!\\.]","").replaceAll("\\s+", " ");
					channelName = channelName.substring(0, Math.min(channelName.length(), 26));
				}
				if (Is.nullOrEmpty(channelName)){
					JSONObject msgJSON = JSON.make("result", "fail", "error", "parameter 'channelName' is null or empty (after clean-up).");
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}
				JSONArray initialMembers = params.getJsonArray("members");		//TODO: validate array of user IDs
				String ownerId = userAccount.getUserID();
				boolean isPublic = params.getBoolOrDefault("isPublic", false);
				boolean addAssistant = params.getBoolOrDefault("addAssistant", true);
				
				//build members array
				Set<String> members = new HashSet<>();
				members.add(ownerId);
				if (initialMembers != null && !initialMembers.isEmpty()){
					for (Object o : initialMembers){
						String m = (String) o;
						if (m.matches("^[A-Za-z]+\\d+$")){		//TODO: prevent more IDs here?
							members.add((String) o);
						}else{
							Debugger.println("createChannel - removed potential member from array due to wrong format: " + m, 1);
						}
					}
				}
				
				//build channel
				try{
					//check number of allowed own channels
					if (!userAccount.hasRole(Role.superuser.name())){
						List<SocketChannel> socketChannelsOfUser = SocketChannelPool.getAllChannelsOwnedBy(ownerId);
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
					SocketChannel sc = SocketChannelPool.createChannel(channelId, ownerId, isPublic, channelName, members, addAssistant);
					if (sc != null){
						String channelKey = sc.getChannelKey();
						JSONObject msgJSON = JSON.make(
								"result", "success",	
								"key", channelKey, 
								"channelId", channelId,
								"channelName", channelName,
								"owner", ownerId
						);
						
						//statistics
						Statistics.addOtherApiHit("createChannel");
						Statistics.addOtherApiTime("createChannel", tic);
						
						return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
					
					}else{
						//error
						JSONObject msgJSON = JSON.make("result", "fail", "error", "creation failed! Maybe already exists?");
						
						//statistics
						Statistics.addOtherApiHit("createChannel-fail");
						Statistics.addOtherApiTime("createChannel-fail", tic);
						
						return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
					}
				}catch (Exception e){
					//error
					JSONObject msgJSON = JSON.make("result", "fail", "error", e.getMessage());
					
					//statistics
					Statistics.addOtherApiHit("createChannel-error");
					Statistics.addOtherApiTime("createChannel-error", tic);
					
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}	
			}else{
				//refuse
				JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized, missing required role");
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
    }
    
    /**
     * Delete existing channel.
     */
    public static String deleteChannel(Request request, Response response){
    	//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	String channelId = params.getString("channelId");
    	
    	//validate
		if (Is.nullOrEmpty(channelId)){
			JSONObject msgJSON = JSON.make("result", "fail", "error", "parameter 'channelId' required.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
		}
		//filter system channels
		if (SocketChannel.systemChannels.contains(channelId)){
			JSONObject msgJSON = JSON.make("result", "fail", "error", "invalid channelId.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
		}
		
		//channel data
		SocketChannel sc = SocketChannelPool.getChannel(channelId);
		if (sc == null){
			JSONObject msgJSON = JSON.make("result", "fail", "error", "channel not found (or temporary access problem).");	//TODO: we should improve the error
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
		}
		
		//cannot delete private channel
		if (channelId.equalsIgnoreCase(sc.getOwner())){
			JSONObject msgJSON = JSON.make("result", "fail", "error", "cannot delete this channel.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
		}
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			long tic = Debugger.tic();
			
			//allowed?
			String userId = userAccount.getUserID();
			if (!sc.getOwner().equals(userId) && !userAccount.hasRole(Role.superuser.name())){
				JSONObject msgJSON = JSON.make("result", "fail", "error", "not allowed.");
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}else{
				//delete
				if (!SocketChannelPool.deleteChannel(sc)){
					JSONObject msgJSON = JSON.make("result", "fail", "error", "failed to delete channel, unknown error, plz try again.");
					
					//statistics
					Statistics.addOtherApiHit("deleteChannel-error");
					Statistics.addOtherApiTime("deleteChannel-error", tic);
					
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}else{
					//TODO: kick out all users active in channel? Or broadcast to channel that it has been removed?
					
					//all good
					JSONObject msgJSON = JSON.make(
							"result", "success",
							"channelId", channelId,
							"channelName", sc.getChannelName()
					);
					
					//statistics
					Statistics.addOtherApiHit("deleteChannel");
					Statistics.addOtherApiTime("deleteChannel", tic);
					
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
    }
    
    /**
     * Join a channel.
     */
    public static String joinChannel(Request request, Response response){
    	//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	String channelId = params.getString("channelId");
    	String userKey = params.getString("userKey");
		String channelKey = params.getString("channelKey");
		
		//validate
		if (Is.nullOrEmpty(channelId)){
			JSONObject msgJSON = JSON.make("result", "fail", "error", "parameter 'channelId' required.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
		}
		if (Is.nullOrEmpty(userKey) && Is.nullOrEmpty(channelKey)){
			JSONObject msgJSON = JSON.make("result", "fail", "error", "parameter 'userKey' OR 'channelKey' required.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
		}
		
		//channel data
		SocketChannel sc = SocketChannelPool.getChannel(channelId);
		if (sc == null){
			JSONObject msgJSON = JSON.make("result", "fail", "error", "channel not found (or temporary access problem).");	//TODO: we should improve the error
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
		}
		
		//cannot join private channel
		if (channelId.equalsIgnoreCase(sc.getOwner())){
			JSONObject msgJSON = JSON.make("result", "fail", "error", "cannot join this channel.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
		}
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			long tic = Debugger.tic();
			
			boolean isAllowed;
			String userId = userAccount.getUserID();
			//Already in?
			if (sc.isUserMemberOfChannel(userId)){
				isAllowed = true;
			//User key
			}else if (Is.notNullOrEmpty(userKey)){
				isAllowed = sc.checkUserOrChannelKey(userKey, userId);
			//Channel key
			}else{
				//TODO: add user-role check here ?
				isAllowed = sc.checkUserOrChannelKey(channelKey, null);
			}
			if (!isAllowed){
				//refuse
				JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized to join channel.");
				
				//statistics
				Statistics.addOtherApiHit("joinChannel-fail");
				Statistics.addOtherApiTime("joinChannel-fail", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}else{
				//add member and store changes
				SocketChannelPool.addMembersToChannel(sc, Arrays.asList(userId));
				//all good
				JSONObject msgJSON = JSON.make(
						"result", "success",
						"channelId", channelId,
						"channelName", sc.getChannelName(),
						"owner", sc.getOwner()
				);
				
				//statistics
				Statistics.addOtherApiHit("joinChannel");
				Statistics.addOtherApiTime("joinChannel", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
    }
    
    /**
     * Get channels available to the user (every channel that he's a member of). Optionally exclude public channels.
     */
    public static String getAvailableChannels(Request request, Response response){
    	//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	boolean includePublic = params.getBoolOrDefault("includePublic", true);
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			long tic = Debugger.tic();
			
			//get ID and data
			String userId = userAccount.getUserID();
			List<SocketChannel> channels = SocketChannelPool.getAllChannelsAvailableTo(userId, includePublic);
			
			//check result
			if (channels == null){
				//error
				JSONObject msgJSON = JSON.make("result", "fail", "error", "failed to get channel data (database error?).");
				
				//statistics
				Statistics.addOtherApiHit("getAvailableChannels-error");
				Statistics.addOtherApiTime("getAvailableChannels-error", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}else{
				//convert result
				JSONArray channelsArray = SocketChannelPool.convertChannelListToClientArray(channels, userId);
				
				//all good
				JSONObject msgJSON = JSON.make(
						"result", "success",
						"channels", channelsArray
				);
				
				//statistics
				Statistics.addOtherApiHit("getAvailableChannels");
				Statistics.addOtherApiTime("getAvailableChannels", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
    }
    
    /**
     * Get channels of the user that are marked for 'missed messages'.<br>
     * NOTE: This data can (and should?) be requested via socket connection as well.
     */
    public static String getChannelsWithMissedMessages(Request request, Response response){
    	//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			long tic = Debugger.tic();
			
			//get ID and data
			String userId = userAccount.getUserID();
			Set<String> channels = SocketChannelHistory.getAllChannelsWithMissedMassegesForUser(userId);
			
			//check result
			if (channels == null){
				//error
				JSONObject msgJSON = JSON.make("result", "fail", "error", "failed to get channel data (database error?).");
				
				//statistics
				Statistics.addOtherApiHit("getChannelsWithMissedMessages-error");
				Statistics.addOtherApiTime("getChannelsWithMissedMessages-error", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}else{
				//convert result
				//JSONArray channelsArray = new JSONArray();
				
				//all good
				JSONObject msgJSON = JSON.make(
						"result", "success",
						"channels", channels
				);
				
				//statistics
				Statistics.addOtherApiHit("getChannelsWithMissedMessages");
				Statistics.addOtherApiTime("getChannelsWithMissedMessages", tic);
				
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized.");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
    }
    
    /**
     * Create a new channel.
     */
    public static String getChannelHistoryStatistic(Request request, Response response){
    	//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			long tic = Debugger.tic();
			
			//must be superuser
			if (userAccount.hasRole(Role.user.name())){
				JSONArray data = SocketChannelHistory.getChannelHistoryInfo();
				
				if (Is.nullOrEmpty(data)){
					JSONObject msgJSON = JSON.make(
							"result", "fail",
							"error", "data was empty, maybe no channels with history exist or data was not cached yet. Check server log for errors!"
					);
					
					//statistics
					Statistics.addOtherApiHit("getChannelHistoryStatistic-fail");
					Statistics.addOtherApiTime("getChannelHistoryStatistic-fail", tic);
					
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}else{
					JSONObject msgJSON = JSON.make(
							"result", "success",	
							"info", SocketChannelHistory.getChannelHistoryInfo()
					);
					
					//statistics
					Statistics.addOtherApiHit("getChannelHistoryStatistic");
					Statistics.addOtherApiTime("getChannelHistoryStatistic", tic);
					
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);	
				}
			}else{
				//refuse
				JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized, missing required role");
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
    }

    /**
     * Clean up old channel history, e.g. messages that were once polled from cache and marked for deletion.  
     */
	public static Object removeOutdatedChannelMessages(Request request, Response response){
		//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			long tic = Debugger.tic();
			
			//must be superuser
			if (userAccount.hasRole(Role.user.name())){
				JSONObject res = SocketChannelHistory.cleanUpOldChannelHistories();
				if (res != null){
					JSONObject msgJSON = JSON.make(
							"result", "success",
							"info", res
					);
					
					//statistics
					Statistics.addOtherApiHit("removeOutdatedChannelMessages");
					Statistics.addOtherApiTime("removeOutdatedChannelMessages", tic);
					
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}else{
					JSONObject msgJSON = JSON.make(
							"result", "fail",
							"error", "no outdated data was removed. Reason unknown, check server logs plz."
					);
					
					//statistics
					Statistics.addOtherApiHit("removeOutdatedChannelMessages-fail");
					Statistics.addOtherApiTime("removeOutdatedChannelMessages-fail", tic);
					
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}
			}else{
				//refuse
				JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized, missing required role");
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
	}
}
