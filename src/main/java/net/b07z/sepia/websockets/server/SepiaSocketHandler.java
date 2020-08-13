package net.b07z.sepia.websockets.server;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.websocket.api.*;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.tools.DateTime;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketMessage.DataType;
import net.b07z.sepia.websockets.common.SocketMessage.SenderType;
import net.b07z.sepia.websockets.common.SocketMessage.TextType;
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketUserPool;
/**
 * WebSocket server implementation for SEPIA messages.<br>
 * Note: annotations are not necessary here, they are inside AbstractSocketHandler. Assign this class to
 * AbstractSocketHandler.SocketServer.server.
 * 
 * @author Florian Quirin
 *
 */
public class SepiaSocketHandler implements SocketServer {
	
	static Logger log = LoggerFactory.getLogger(SepiaSocketHandler.class);
	
    private static AtomicLong nextUserNumber = new AtomicLong(); 	//Assign to username for next connecting user
	
    //Connect
    public void onConnect(Session userSession) throws Exception {
    	//userSession.getPolicy().setIdleTimeout(SocketConfig.IDLE_TIMEOUT);
   		userSession.getPolicy().setAsyncWriteTimeout(SocketConfig.ASYNC_TIMEOUT);
    	//add pending user session
    	SocketUserPool.storePendingSession(userSession);
        //send authentication request back to user
        JSONObject data = new JSONObject();
        JSON.add(data, "dataType", DataType.authenticate.name());
        SocketMessage msg = new SocketMessage("", SocketConfig.SERVERNAME, SocketConfig.localName, "", "", data);
        broadcastMessage(msg, userSession);
    }

    //Close
    public void onClose(Session userSession, int statusCode, String reason) {
    	SocketUser user = getUserBySession(userSession);
    	if (user != null){
    		removeUser(user);
    		SocketMessage msgListUpdate = SepiaSocketBroadcaster.makeServerStatusMessage(
    				"", user.getActiveChannel(), (user.getUserName() + " (" + user.getUserId() + ") left the chat"), DataType.byebye, true
    		);
    		broadcastMessage(user, msgListUpdate);
    	
    	}else{
    		//none active user left the server
    		SocketUserPool.removePendingSession(userSession);
    	}
    }
    
    //Error
    public void onError(Session userSession, Throwable error) {
    	log.error("SepiaSocketHandler reported: " + error.getMessage());
    	//TODO: implement
    }

    //Message
    public void onMessage(Session userSession, String message) {
    	//System.out.println(message); 		//DEBUG
    	SocketMessage msg;
		try {
			msg = SocketMessage.importJSON(message);
			boolean msgHasData = (msg.data != null && !msg.data.isEmpty());
			String dataType = "";
			if (msgHasData){
				dataType = (String) msg.data.get("dataType");	//TODO: dataType might be missing here
			}
			
			SocketUser user = getUserBySession(userSession);
			
			//IMPORTANT: Make sure the sender is what he pretends to be
			if (user == null){
				msg.sender = "";
			}else{
				msg.sender = user.getUserId();
			}
			if (msg.senderType != null && msg.senderType.equals(SenderType.server.name()) && !msg.sender.equals(SocketConfig.SERVERNAME)){
				msg.senderType = null; 		//don't allow fake SERVER senderType
				//TODO: senderType could be confused with values of "dataType" like "assistAnswer" etc. ... 
			}
			
			//Validate user data
			boolean userDataAccepted = false;
			if (user == null){
				//white-list actions that are possible without stored user
				if (msgHasData && (
						 	dataType.equals(DataType.authenticate.name())
						)){
					userDataAccepted = true;
				}
			}else{
				//we only accept remote actions if they are approved by the assistant BECAUSE:
				//They are redirected from the remote action endpoint of the Assist-Server
				if (msgHasData && dataType.equals(DataType.remoteAction.name())){
					if (user.isAuthenticated() && user.getUserRole().equals(Role.assistant)){
						userDataAccepted = true;
					}else{
						userDataAccepted = false;
					}
				//everything else should be OK
				}else{
					userDataAccepted = true;
				}
				//DEBUG
				/*
				if (userDataAccepted){
					System.out.println("UserId: " + user.getUserId());
					System.out.println("UserChannel: " + user.getActiveChannel());
					System.out.println("UserActive: " + user.isActive());
				}
				*/
			}
			
			//Validate channel
			boolean channelAccepted = false;
			String channelId = msg.channelId;
			if (channelId == null || channelId.isEmpty()){
				//white-list actions that are possible without channel
				if (msgHasData && (
							dataType.equals(DataType.joinChannel.name()) 
						|| 	dataType.equals(DataType.authenticate.name())
						|| 	dataType.equals(DataType.ping.name())
						)){
					channelAccepted = true;
				}
			}else if (channelId.equals("<auto>")){
				//get active channel
				if (user != null){
					//do we have a user that can be in any channel? Then get the receiver active channel
					if (user.isOmnipresent()){
						SocketUser rec = SocketUserPool.getActiveUserById(msg.receiver);
						if (rec != null){
							channelId = rec.getActiveChannel(); //TODO: does it make sense to broadcast this to all users with this ID not only active?
							msg.channelId = channelId;			//refresh
							channelAccepted = true;
						}
					}else{
						channelId = user.getActiveChannel();	//Note: I wonder what happens when the user is the assistant that is active in all channels?
						msg.channelId = channelId;				//refresh
						//check this channel here or assume that all "active channels" really exist and the user is allowed to use it?
						channelAccepted = true;
					}
				}
			}else{
				//validate channel - TODO: this procedure has potential to fail when channel operations are not in sync with user, I'm sure, I think, maybe ... ^^ 
				//user must exists if a message should be sent to channel
				if (user != null){
					SocketChannel testSc = SocketChannelPool.getChannel(channelId);
					//channel exists?
					if (testSc != null){
						//user is active in this channel?
						if (!user.getActiveChannel().equals(channelId)){
							if (testSc.isUserMemberOfChannel(user)){
								//user.setActiveChannel(channelId); 		//do this here?
								channelAccepted = true;
							}
						}else{
							channelAccepted = true;
						}
					}
				}
			}
			
			//Validation summary
			boolean isValidMessage = userDataAccepted && channelAccepted;
			if (!isValidMessage){
				log.info("Message failed the 'SocketUser' or 'channelId' test! - Message (safe): " 
						+ SepiaSocketBroadcaster.makeSafeMessage(msg).toJSONString());
			}
			//if we reach this point it means: a) we have a valid user and channel or b) we don't need them
			
			//check data
			if (isValidMessage && msgHasData){
				
				//simply broadcast
				if (dataType == null 
						|| dataType.equals(DataType.openText.name())
						|| dataType.equals(DataType.assistAnswer.name())
						|| dataType.equals(DataType.assistFollowUp.name())
						|| dataType.equals(DataType.directCmd.name())
						){
					broadcastMessage(user, msg);
					
				//error broadcast
				}else if (dataType.equals(DataType.errorMessage.name())){
					msg.textType = TextType.status.name(); 			//force status text
					broadcastMessage(user, msg);
				
				//broadcast default welcome and byebye
				/* -- has been disabled, for now only server is allowed to send it --
				}else if (dataType.equals(DataType.welcome.name())){
					String senderName = (String) msg.data.get("username");
					SocketMessage msgWelcome = makeServerStatusMessage(msgId, channelId, 
							(((senderName != null)? senderName : "???") + " (" + msg.sender + ") joined the chat"), 
							DataType.welcome, true);
			        broadcastMessage(msgWelcome);
			    */
					
				//ping request or reply
				}else if (dataType.equals(DataType.ping.name())){
					ServerMessageHandler smh = new SepiaClientPingHandler(this);
					smh.handle(userSession, msg);
			        
				//authenticate
				}else if (dataType.equals(DataType.authenticate.name())){
					ServerMessageHandler smh = new SepiaAuthenticationHandler(this);
					smh.handle(userSession, msg);
					
				//join channel
				}else if (dataType.equals(DataType.joinChannel.name())){
					ServerMessageHandler smh = new SepiaChannelJoinHandler(this);
					smh.handle(userSession, msg);
					
				//update data (request)
				}else if (dataType.equals(DataType.updateData.name())){
					ServerMessageHandler smh = new SepiaUpdateDataHandler(this);
					smh.handle(userSession, msg);
									
			    //broadcast remote action - note: a remote action needs to be validated by an assistant (see checks above)
				}else if (dataType.equals(DataType.remoteAction.name())){
					ServerMessageHandler smh = new SepiaRemoteActionHandler(this);
					smh.handle(userSession, msg);
			        
				//unknown dataTypes
				}else{
					log.error(DateTime.getLogDate() + " ERROR - " + this.getClass().getName() + " - unhandled message dataType: " + dataType);
				}
			
			//all others are simply broadcasted
			}else if (isValidMessage){
				broadcastMessage(user, msg);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    //-------------- broadcasting ----------------
    
    //Sends message to automatically selected list of users
    @Override
    public void broadcastMessage(SocketUser source, SocketMessage msg){
		SepiaSocketBroadcaster.preBroadcastAction(source, false, false, false);
    	SepiaSocketBroadcaster.broadcastMessage(msg);
    }
    //Sends message to all active users of a certain channel - message assumes channelId was checked before
    @Override
    public void broadcastMessage(SocketMessage msg, String channelId){
    	SepiaSocketBroadcaster.broadcastMessageToChannel(msg, channelId);
    }
    //sends a message to user of given session
    @Override
    public void broadcastMessage(SocketMessage msg, Session session) {
    	SepiaSocketBroadcaster.broadcastMessageToSession(msg, session);
    }
    
    @Override
	public long getLastBroadcastTime() {
    	return SepiaSocketBroadcaster.getLastBroadcastTime();
	}
    
    //-------------- user handling ----------------
    
    @Override
    public String makeUserName(){
    	long id = nextUserNumber.incrementAndGet();
    	String username = SocketUser.GUEST + id;
    	return username;
    }

	@Override
	public void storeUser(SocketUser socketUser) {
		SocketUserPool.storeUser(socketUser);
	}

	@Override
	public void removeUser(SocketUser socketUser) {
		SocketUserPool.removeUser(socketUser);
	}

	@Override
	public SocketUser getUserBySession(Session session) {
		return SocketUserPool.getUserBySession(session);
	}

	@Override
	public Collection<SocketUser> getAllUsers() {
		return SocketUserPool.getAllUsers();
	}

}
