package net.b07z.sepia.websockets.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketUserPool;
import net.b07z.sepia.websockets.common.SocketMessage.DataType;
import net.b07z.sepia.websockets.common.SocketMessage.TextType;

/**
 * Broadcast WebSocket messages from server to client. NOTE: This is a static implementation meant to be used with {@link SepiaSocketHandler}.
 * 
 * @author Florian Quirin
 *
 */
public class SepiaSocketBroadcaster {
	
	private static long timeOfLastBroadcast = 0;
	
	/**
     * Make a status message with certain configuration (receiver is all, but you can send it to a certain userSession to make it private).
     * @param msgId - message ID if known or null
     * @param channelId - channel to receive message
     * @param text - status text (TextType.status is set automatically)
     * @param dataType - any value of {@link DataType}. Use socketMessage.addData(...) to add specific data for type later.
     * @param addActiveChannelUsersList - add list of active users to update channel data on client?
     */
    public static SocketMessage makeServerStatusMessage(String msgId, String channelId, String text, DataType dataType, boolean addActiveChannelUsersList){
    	SocketMessage msg = new SocketMessage(channelId, SocketConfig.SERVERNAME, SocketConfig.localName, "", "", 
    			text, TextType.status.name());
		if (dataType != null) 					msg.addData("dataType", dataType.name());
        if (msgId != null && !msgId.isEmpty()) 	msg.setMessageId(msgId);
		if (addActiveChannelUsersList){
			//NOTE: you have to make sure that getChannel is not null by checking before if the channel still exists
			msg.setUserList(SocketChannelPool.getChannel(channelId).getActiveMembers(true));
		}
        return msg;
    }
    
    /**
     * Make a message that tells the client to update specific data. Should be sent to a user-session (since it has no channel and receiver).
     * @param updateType - e.g. "missedChannelMessage" or "availableChannels"
     * @param data - preferably a JSONObject or JSONArray. If this is empty the client should call the request method itself.
     * @return {@link SocketMessage}
     */
    public static SocketMessage makeServerUpdateDataMessage(String updateType, Object data){
    	SocketMessage msgUpdateData = new SocketMessage(null, SocketConfig.SERVERNAME, SocketConfig.localName, null, null, JSON.make(
        		"dataType", DataType.updateData.name(),
        		"updateData", updateType,
        		"data", data
        ));
    	return msgUpdateData;
    }
    
    //make a safe message for all the users/objects in the channel that should not get sensitive data
    public static JSONObject makeSafeMessage(SocketMessage msg){
    	//note: we use the 'getJSON' here to clone the object
    	JSONObject safeMsg = msg.getJSON();
    	if (safeMsg.containsKey("data")){
			JSONObject safeData = new JSONObject((JSONObject) safeMsg.get("data"));
			if (safeData != null){
				safeData.remove("credentials");
				safeData.remove("parameters");
				JSON.put(safeMsg, "data", safeData);
			}
		}
    	return safeMsg;
    }
    //check for safe receiver
    public static boolean isTrustyReceiver(SocketUser user){
    	if (user == null){
    		return false;
    	}
    	Role receiverRole = user.getUserRole();
    	if (receiverRole == null || !receiverRole.equals(Role.assistant)){
    		return false;
    	}else{
    		return true;
    	}
    }
    
    /**
     * Convert assistant message to openText message e.g. for "safe" channel history.
     * @return new {@link SocketMessage} or null
     */
    public static SocketMessage buildOpenTextFromAssistantMessage(SocketMessage msg){
    	try {
    		JSONObject nuData = new JSONObject();
    		
    		//we can keep card data
    		JSONObject assistAnswer = JSON.getJObject(msg.data, "assistAnswer");
    		boolean hasCard = JSON.getBoolean(assistAnswer, "hasCard");
    		if (hasCard){
    			JSON.put(nuData, "assistAnswer", JSON.make(
    					"hasCard", true,
    					"cardInfo", assistAnswer.get("cardInfo")
    			));
    		}
    		//System.out.println("assistAnswer: " + assistAnswer);		//DEBUG
    		
    		//build new msg
	    	SocketMessage nuMsg = new SocketMessage(msg.channelId, msg.sender, msg.senderDeviceId, msg.receiver, msg.receiverDeviceId, nuData);
	    	nuMsg.msgId = msg.msgId;
	    	nuMsg.text = JSON.getString(assistAnswer, "answer_clean");
	    	nuMsg.setDataType(DataType.openText);
	    	return nuMsg;
	    	
    	}catch (Exception e){
    		e.printStackTrace();
    		return null;
    	}
    }
    
    //When was the last broadcast
    public static long getLastBroadcastTime(){
    	return timeOfLastBroadcast;
    }
    
    //Action to be executed pre-broadcast, depending on parameters of broadcast
    public static void preBroadcastAction(SocketUser user, boolean isSingleSession, boolean isAllSessions, boolean skipActiveCheck){
    	if (user != null){
    		user.setActive();
    		
	    	if (!skipActiveCheck){
	    		//check conflicts with multiple logins of same ID:
		    	if (!isSingleSession && !isAllSessions && !user.getUserId().isEmpty()){
					//this might be a bit costly, but for now we deactivate all users that are active with the same id in the same channel
					List<SocketUser> deactivatedUsers = SocketUserPool.setUsersWithSameIdInactive(user);
					//and just in case the current user-session was inactive:
					user.setActive();
					//inform the inactive sessions
			        SocketMessage msgStatusUpdate = makeServerStatusMessage(
			        		"", user.getActiveChannel(), 
			        		"Your session is now inactive in channel (" + user.getActiveChannel() + ") until you send a message", 
			        		DataType.byebye, true
			        );
			        broadcastMessageToSocketUsers(msgStatusUpdate, deactivatedUsers);
		    	}
	    	}
    	}
    }
    
    //Sends message to automatically selected list of users
    public static void broadcastMessage(SocketMessage msg){
    	if (msg.userListCollection != null){
    		broadcastMessageToSocketUsers(msg, msg.userListCollection);
    	
    	}else if (msg.channelId != null && !msg.channelId.isEmpty()){
    		broadcastMessageToChannel(msg, msg.channelId);
    	
    	}else if (msg.receiver != null && !msg.receiver.isEmpty()){
    		//TODO: no channel, no session, no collection 
    		//... probably better to prevent that this message is sent at all because the user can't block it yet!
    		//
    		//Other option could be to build a collection list using the single receiver ID:  
    		//if (msg.sender != null && !msg.sender.isEmpty()){}
    		//broadcastMessage(msg, SocketUserPool.getAllUsersById(msg.receiver));
    	}
    }
    
    //Sends message to all active users of a certain channel - message assumes channelId was checked before
    public static void broadcastMessageToChannel(SocketMessage msg, String channelId){
    	//TODO: check channel "<auto>" again? - should have been replaced in channel-check at start ...
    	SocketChannel sc = SocketChannelPool.getChannel(channelId);
    	if (sc == null){
    		//channel does not exist (anymore?)
    		//TODO: return message to sender with 'missing channel' note
    	}else{
    		//public channel
    		if (sc.isOpen()){
    			//in public channels only active members get messages
    			List<SocketUser> activeChannelUsers = sc.getActiveMembers(false);
    			broadcastMessageToSocketUsers(msg, activeChannelUsers);
    		
    		//private channel
    		}else if (sc.getOwner().equalsIgnoreCase(channelId)){
    			//in private channels messages target device IDs
    			List<SocketUser> activeChannelUsers = sc.getActiveMembers(false);
    			if (SocketConfig.inUserChannelBroadcastOnlyToAssistantAndSelf && msg.sender.equalsIgnoreCase(channelId)){
    	    		//get only sender (userId + deviceId) and assistant
    				activeChannelUsers = activeChannelUsers.stream().filter(su -> {
    					return (su.getDeviceId().equalsIgnoreCase(msg.senderDeviceId) || su.getUserId().equalsIgnoreCase(ConfigDefaults.defaultAssistantUserId)); 
    				}).collect(Collectors.toList());
    	    	}
    			broadcastMessageToSocketUsers(msg, activeChannelUsers);
    		
    		//other channels
    		}else{
    			List<SocketUser> inactiveChannelUsers = new ArrayList<>();
    			List<SocketUser> activeChannelUsers = new ArrayList<>();
    			Set<String> offlineOrInactiveChannelUsers = new HashSet<>(sc.getAllRegisteredMembersById());
    			offlineOrInactiveChannelUsers.remove(msg.sender);
    			sc.getAllOnlineMembers().forEach((su) -> {
    				if (su.isActiveInChannelOrOmnipresent(channelId)){
    					activeChannelUsers.add(su);
    					offlineOrInactiveChannelUsers.remove(su.getUserId());
    				}else{
    					inactiveChannelUsers.add(su);
    				}
    			});
    			//broadcast to active users in channel
    			broadcastMessageToSocketUsers(msg, activeChannelUsers);
    			
    			//broadcast 'check channel' to online users
    			JSONArray data = new JSONArray();
    			JSON.add(data, JSON.make("channelId", channelId));
    			SocketMessage msgUpdateData = makeServerUpdateDataMessage(
    					"missedChannelMessage", data
    			);
    			broadcastMessageToSocketUsers(msgUpdateData, inactiveChannelUsers);
    			
    			//build (filtered) channel history and notify users of missed messages
    			String dataType = msg.getDataType();
    			if (dataType != null){
    				boolean registerAsMissed = false;
    				if (msg.receiver == null && (dataType.equals(DataType.assistAnswer.name()) || dataType.equals(DataType.assistFollowUp.name()))){
						//convert assistant to "normal" text message - its for safety and to prevent uncontrollable command executions (TODO: improve)
    					SocketMessage newMsg = buildOpenTextFromAssistantMessage(msg);
    					if (newMsg != null){
    		    			//store NON-PRIVATE message in channel history
   		    				SocketChannelHistory.addMessageToChannelHistory(channelId, newMsg);
   		    				registerAsMissed = true;
    					}
					}else if (dataType.equals(DataType.openText.name())){
		    			//store NON-PRIVATE message in channel history
		    			if (msg.receiver == null){
		    				SocketChannelHistory.addMessageToChannelHistory(channelId, msg);
		    			}
		    			registerAsMissed = true;
    				}
    				if (registerAsMissed){
    					//register missed message for inactive and offline users
		    			for (String userId : offlineOrInactiveChannelUsers){
		    				//NON-PRIVATE or ID match
		    				if (msg.receiver == null || msg.receiver.equals(userId)){
		    					SocketChannelHistory.addChannelWithMissedMessagesForUser(userId, channelId);
		    				}
		    			}
    				}
    			}
    		}
    	}
    }
    
    //Sends message to a list of active channel users
    public static void broadcastMessageToSocketUsers(SocketMessage msg, Collection<SocketUser> userList){
    	//to all users
    	if (msg.receiver == null || msg.receiver.isEmpty()){
    		//old code: getAllUsers().stream().filter(Session::isOpen).forEach(session -> {
    		for (SocketUser su : userList){
    			//System.out.println("(1) Broadcast from " + msg.sender + " to " + su.getUserId() + " with role " + su.getUserRole());	//debug
    			//is user-session still open?
    			if (!su.getUserSession().isOpen()){
    				SocketUserPool.removeUser(su);
    			}else{
    				//make message safe if receiver is not trusty
	    			if (!isTrustyReceiver(su)){
	    				//filter some stuff like prevent slash-command repost
	    				if (msg.text != null && msg.text.matches("(\\w+ |^)saythis .*")){ 		
	    					//TODO: what about the other slash-commands?? (linkshare, http(s|), ...)
	    					return;
	    				}
	    				//don't send credentials when the receiver is not an assistant (or another trustworthy receiver)
	    				JSONObject safeMsg = makeSafeMessage(msg);
	    				//System.out.println("(1) Send safe data: " + safeMsg);		//debug
	    				broadcastNow(safeMsg, su.getUserSession());
	    			}else{
	    				JSONObject fullMsg = msg.getJSON();
	    				//System.out.println("(1) Send unsafe data: " + fullMsg);		//debug
	    				broadcastNow(fullMsg, su.getUserSession());
	    			}
    			}
            }
    		
    	//to Server?
    	}else if (msg.receiver.equals(SocketConfig.SERVERNAME)){
    		//dead end
    	
    	//to single user
    	}else{
    		//System.out.println("(2) Broadcast from " + msg.sender);		//debug
    		/*
    		System.out.println("msg.receiver: " + msg.receiver);
			System.out.println("msg.receiverDeviceId: " + msg.receiverDeviceId);
			System.out.println("msg.sender: " + msg.sender);
			System.out.println("msg.senderDeviceId: " + msg.senderDeviceId);
			*/
    		userList.stream().filter(u -> {
    			/*
    			System.out.println("filter userId: " + u.getUserId());
    			System.out.println("filter deviceId: " + u.getDeviceId());
    			*/
    			boolean isReceiver = u.getUserId().equalsIgnoreCase(msg.receiver) 
    					&& (!SocketConfig.distinguishUsersByDeviceId || u.getDeviceId().equalsIgnoreCase(msg.receiverDeviceId));
    			boolean isSender = u.getUserId().equalsIgnoreCase(msg.sender)
    					&& (!SocketConfig.distinguishUsersByDeviceId || u.getDeviceId().equalsIgnoreCase(msg.senderDeviceId));
    			return (isReceiver || isSender);
    		}).forEach(u -> {
    			//System.out.println("(2) to: " + u.getUserId() + ", " + u.getDeviceId());		//debug
    			//will check receiver AND sender:
    			if (!u.getUserSession().isOpen()){
    				SocketUserPool.removeUser(u);
    			}else{
    				Session recSession = u.getUserSession();
    				boolean isTrusty = isTrustyReceiver(u);
    				//make 2 messages, one safe one with credentials
    	    		if (recSession != null){
    	    			if (!isTrusty){
    	    				JSONObject safeMsg = makeSafeMessage(msg);
    	        			//don't send credentials when the receiver is not an assistant (or another trustworthy receiver)
    	    				//System.out.println("(2) Send safe data: " + safeMsg);		//debug
    	    				broadcastNow(safeMsg, recSession);
    	    			}else{
    	    				JSONObject fullMsg = msg.getJSON();
    	    				//System.out.println("(2) Send unsafe data: " + fullMsg);		//debug
    	    				broadcastNow(fullMsg, recSession);
    	        		}
    	    		}
    	    		//confirmation to user is included in filter
    			}
    		});
    	}
    }
    
    //sends a message to user of given session
    public static void broadcastMessageToSession(SocketMessage msg, Session session) {
    	SocketUser su = SocketUserPool.getUserBySession(session);
    	if (su == null){
    		broadcastNow(makeSafeMessage(msg), session); 		//TODO: is this limiting some authentication process?
    	}else{
    		su.setActive();		//TODO: do we really want this? ... and do we really need all the following code here ... ?
    		Collection<SocketUser> userList = new ArrayList<>();
	    	userList.add(su);
	    	msg.receiver = su.getUserId();
	    	msg.receiverDeviceId = su.getDeviceId();
	    	//this will take care of making the message safe (and sending it to receiver and sender (confirmation) ??? I think this is not valid anymore ...)
	    	broadcastMessageToSocketUsers(msg, userList);
    	}
    }
    
    //sends a message to user of given session - better not use this directly 'cause that would skip the safety procedures
    private static void broadcastNow(JSONObject msg, Session session) {
    	//System.out.println(msg); 		//DEBUG
       	try {
            session.getRemote().sendString(msg.toJSONString());
            timeOfLastBroadcast = System.currentTimeMillis();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
