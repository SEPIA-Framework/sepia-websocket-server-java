package net.b07z.sepia.websockets.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketChannelPool;
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
     * @param msgId
     * @param channelId
     * @param text
     * @param dataType
     * @param addActiveChannelUsersList
     */
    public static SocketMessage makeServerStatusMessage(String msgId, String channelId, String text, DataType dataType, boolean addActiveChannelUsersList){
    	SocketMessage msg = new SocketMessage(channelId, SocketConfig.SERVERNAME, "", text, TextType.status.name());
		if (dataType != null) 					msg.addData("dataType", dataType.name());
        if (msgId != null && !msgId.isEmpty()) 	msg.setMessageId(msgId);
		if (addActiveChannelUsersList){
			msg.setUserList(SocketChannelPool.getChannel(channelId).getActiveMembers(true));
		}
        return msg;
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
    
    //When was the last broadcast
    public static long getLastBroadcastTime(){
    	return timeOfLastBroadcast;
    }
    
    //Action to be executed pre-broadcast, depending on parameters of broadcast
    public static void preBroadcastAction(SocketUser user, boolean isSingleSession, boolean isAllSessions, boolean skipActiveCheck){
    	if (user != null){
    		user.setActive();
    	}
    	if (!skipActiveCheck){
    		//check conflicts with multiple logins of same ID:
	    	if (!isSingleSession && !isAllSessions 
	    		&& user != null && !user.getUserId().isEmpty()){
	    		
				//this might be a bit costly, but for now we deactivate all users that are active with the same id in the same channel
				List<SocketUser> deactivatedUsers = SocketUserPool.setUsersWithSameIdInactive(user.getUserId(), user.getUserSession(), user.getActiveChannel());
				//and just in case the current user-session was inactive:
				user.setActive();
				//inform the inactive sessions
		        SocketMessage msgStatusUpdate = makeServerStatusMessage("", user.getActiveChannel(), "Your session is now inactive in channel (" + user.getActiveChannel() + ") until you send a message", DataType.byebye, true);
		        broadcastMessage(msgStatusUpdate, deactivatedUsers);
	    	}
    	}
    }
    
    //Sends message to automatically selected list of users
    public static void broadcastMessage(SocketMessage msg){
    	if (msg.userListCollection != null){
    		broadcastMessage(msg, msg.userListCollection);
    	
    	}else if (msg.channelId != null && !msg.channelId.isEmpty()){
    		broadcastMessage(msg, msg.channelId);
    	
    	}else if (msg.receiver != null && !msg.receiver.isEmpty()){
    		//TODO: no channel, no session, no collection ... probably better to prevent that this message is sent at all because the user can't block it yet!
    		//if (msg.sender != null && !msg.sender.isEmpty()){}
    		//broadcastMessage(msg, SocketUserPool.getAllUsersById(msg.receiver));
    	}
    }
    //Sends message to all active users of a certain channel - message assumes channelId was checked before
    public static void broadcastMessage(SocketMessage msg, String channelId){
    	//TODO: check channel "<auto>" again? - should have been replaced in channel-check at start ...
    	SocketChannel sc = SocketChannelPool.getChannel(channelId);
    	List<SocketUser> activeChannelUsers = sc.getActiveMembers(false); 		//TODO: this one is tricky, for normal messages it should be "false" 
    	broadcastMessage(msg, activeChannelUsers);
    }
    //Sends message to a list of active channel users
    public static void broadcastMessage(SocketMessage msg, Collection<SocketUser> userList){
    	//to all users
    	if (msg.receiver == null || msg.receiver.isEmpty()){
    		//make 2 messages, one safe one with credentials
    		JSONObject fullMsg = msg.getJSON();
    		JSONObject safeMsg = makeSafeMessage(msg);
    		
    		//old code: getAllUsers().stream().filter(Session::isOpen).forEach(session -> {
    		for (SocketUser su : userList){
    			//System.out.println("(1) Broadcast from " + msg.sender + " to " + su.getUserId() + " with role " + su.getUserRole());	//debug
    			//is user-session still open?
    			if (!su.getUserSession().isOpen()){
    				SocketUserPool.removeUser(su);
    			}else{
	    			if (!isTrustyReceiver(su)){
	    				//filter some stuff like prevent slash-command repost
	    				if (msg.text != null && msg.text.matches("(\\w+ |^)saythis .*")){
	    					return;
	    				}
	    				//don't send credentials when the receiver is not an assistant (or another trustworthy receiver)
	    				//System.out.println("(1) Send safe data: " + safeMsg);		//debug
	    				broadcastMessage(safeMsg, su.getUserSession());
	    			}else{
	    				//System.out.println("(1) Send unsafe data: " + fullMsg);		//debug
	    				broadcastMessage(fullMsg, su.getUserSession());
	    			}
    			}
            }
    		
    	//to Server?
    	}else if (msg.receiver.equals(SocketConfig.SERVERNAME)){
    		//dead end
    	
    	//to single user
    	}else{
    		//System.out.println("(2) Broadcast from " + msg.sender);		//debug
    		userList.stream().filter(u -> (u.getUserId().equalsIgnoreCase(msg.receiver) || u.getUserId().equalsIgnoreCase(msg.sender))).forEach(u -> {
    			//System.out.println("(2) to: " + u.getUserId());		//debug
    			//will check receiver AND sender:
    			if (!u.getUserSession().isOpen()){
    				SocketUserPool.removeUser(u);
    			}else{
    				Session recSession = u.getUserSession();
    				boolean isTrusty = isTrustyReceiver(u);
    				//make 2 messages, one safe one with credentials
    	    		JSONObject fullMsg = msg.getJSON();
    	    		JSONObject safeMsg = makeSafeMessage(msg);
    	    		if (recSession != null){
    	    			if (!isTrusty){
    	        			//don't send credentials when the receiver is not an assistant (or another trustworthy receiver)
    	    				//System.out.println("(2) Send safe data: " + safeMsg);		//debug
    	    				broadcastMessage(safeMsg, recSession);
    	    			}else{
    	    				//System.out.println("(2) Send unsafe data: " + fullMsg);		//debug
    	    				broadcastMessage(fullMsg, recSession);
    	        		}
    	    		}
    	    		//confirmation to user is included in filter
    			}
    		});
    	}
    }
    //sends a message to user of given session
    public static void broadcastMessage(SocketMessage msg, Session session) {
    	SocketUser su = SocketUserPool.getUserBySession(session);
    	if (su == null){
    		broadcastMessage(makeSafeMessage(msg), session); 		//TODO: is this limiting some authentication process?
    	}else{
    		Collection<SocketUser> userList = new ArrayList<>();
	    	userList.add(su);
	    	msg.receiver = su.getUserId();
	    	broadcastMessage(msg, userList);
    	}
    }
    //sends a message to user of given session - better not use this directly 'cause that would skip the safety procedures
    private static void broadcastMessage(JSONObject msg, Session session) {
    	//System.out.println(msg); 		//DEBUG
       	try {
            session.getRemote().sendString(msg.toJSONString());
            timeOfLastBroadcast = System.currentTimeMillis();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
