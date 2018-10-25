package net.b07z.sepia.websockets.server;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.websocket.api.*;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.server.RequestParameters;
import net.b07z.sepia.server.core.server.RequestPostParameters;
import net.b07z.sepia.server.core.tools.DateTime;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketChannelPool;
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
    private static long timeOfLastBroadcast = 0;
	
    //Connect
    public void onConnect(Session userSession) throws Exception {
    	//userSession.getPolicy().setIdleTimeout(SocketConfig.IDLE_TIMEOUT);
   		userSession.getPolicy().setAsyncWriteTimeout(SocketConfig.ASYNC_TIMEOUT);
    	//add pending user session
    	SocketUserPool.storePendingSession(userSession);
        //send authentication request back to user
        JSONObject data = new JSONObject();
        JSON.add(data, "dataType", DataType.authenticate.name());
        SocketMessage msg = new SocketMessage("", SocketConfig.SERVERNAME, "", data);
        preBroadcastAction(null, msg, true, false, false);
        broadcastMessage(msg, userSession);
    }

    //Close
    public void onClose(Session userSession, int statusCode, String reason) {
    	SocketUser user = getUserBySession(userSession);
    	if (user != null){
    		removeUser(user);
    		SocketMessage msgListUpdate = makeServerStatusMessage("", user.getActiveChannel(), (user.getUserName() + " (" + user.getUserId() + ") left the chat"), DataType.byebye, true);
    		preBroadcastAction(user, msgListUpdate, false, false, false);
    		broadcastMessage(msgListUpdate);
    	
    	}else{
    		//none active user left the server
    		SocketUserPool.removePendingSession(userSession);
    	}
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
			
			String msgId = msg.msgId;
			SocketUser user = getUserBySession(userSession);
			String channelId = msg.channelId;
			SocketChannel sc = null;
			
			//Validate user and channel values
			boolean userDataAccepted = false;
			if (user == null){
				//white-list actions that are possible without stored user
				if (msgHasData && (
						 	dataType.equals(DataType.authenticate.name())
						)){
					userDataAccepted = true;
				}
			}else{
				if (msgHasData && dataType.equals(DataType.remoteAction.name())){
					//we only accept remote actions if they are approved by the assistant
					if (user.isAuthenticated() && user.getUserRole().equals(Role.assistant)){
						//the actual userId is in the data
						String remoteUserId = (String) msg.data.get("user");
						String targetDeviceId = (String) msg.data.get("targetDeviceId");
						boolean channelIdIsAuto = (channelId == null || channelId.isEmpty() || channelId.equals("<auto>"));
						boolean targetDeviceIsAuto = (targetDeviceId == null || targetDeviceId.isEmpty() || targetDeviceId.equals("<auto>"));
						
						if (remoteUserId != null && !remoteUserId.isEmpty()){
							//find the right session
							user = null; //SocketUserPool.getActiveUserById(remoteUserId);
							List<SocketUser> possibleRemoteUsers = SocketUserPool.getAllUsersById(remoteUserId);
							boolean correctDevice = false;
							boolean correctChannel = false;
							for (SocketUser su : possibleRemoteUsers){
								correctDevice = (targetDeviceIsAuto || (targetDeviceId.equals(su.getDeviceId())));
								correctChannel = (channelIdIsAuto || (channelId.equals(su.getActiveChannel())));
								if (correctDevice && correctChannel){
									user = su;
								}
							}
							if (user != null){
								userDataAccepted = true;
								//debug:
								/*
								System.out.println("RemoteUserId: " + user.getUserId());
								System.out.println("TargetChannel: " + channelId);
								System.out.println("TargetDevice: " + targetDeviceId);
								*/
							}
						}
					}else{
						userDataAccepted = false;
					}
				
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
				//need more checks?
			}
			boolean channelAccepted = false;
			if (channelId == null || channelId.isEmpty()){
				//white-list actions that are possible without channel
				if (msgHasData && (
							dataType.equals(DataType.joinChannel.name()) 
						|| 	dataType.equals(DataType.authenticate.name())
						)){
					channelAccepted = true;
				}
			}else if (channelId.equals("<auto>")){
				//get active channel
				if (user != null){
					channelId = user.getActiveChannel();
					channelAccepted = true;
					//check this channel here or assume that all "active channels" really exist and the user is allowed to use it?
				}
			}else{
				//validate channel - TODO: this procedure has potential to fail when channel operations are not in sync with user, I'm sure, I think, maybe ... ^^ 
				//user must exists if a message should be sent to channel
				if (user != null){
					sc = SocketChannelPool.getChannel(channelId);
					//channel exists?
					if (sc != null){
						//user is active in this channel?
						if (!user.getActiveChannel().equals(channelId)){
							if (sc.isUserMemberOfChannel(user)){
								//user.setActiveChannel(channelId); 		//do this here?
								channelAccepted = true;
							}
						}else{
							channelAccepted = true;
						}
					}
				}
			}
			boolean isValidMessage = userDataAccepted && channelAccepted;
			if (!isValidMessage){
				log.info("Message failed the 'SocketUser' or 'channelId' test! - Message (safe): " + makeSafeMessage(msg).toJSONString());
			}
			//if we reach this point it means: a) we have a valid user and channel or b) we don't need them

			//make sure the sender is what he pretends to be
			if (user == null){
				msg.sender = "";
			}else{
				msg.sender = user.getUserId();
			}
			if (msg.senderType != null && msg.senderType.equals(SenderType.server.name()) && !msg.sender.equals(SocketConfig.SERVERNAME)){
				msg.senderType = null; 		//don't allow fake SERVER senderType
				//TODO: senderType could be confused with values of "dataType" like "assistAnswer" etc. ... 
			}
			
			//check data
			if (isValidMessage && msgHasData){
				
				//simply broadcast
				if (dataType == null 
						|| dataType.equals(DataType.openText.name())
						|| dataType.equals(DataType.assistAnswer.name()) 
						|| dataType.equals(DataType.directCmd.name())
						){
					preBroadcastAction(user, msg, false, false, false);
					broadcastMessage(msg);
					
				//error broadcast
				}else if (dataType.equals(DataType.errorMessage.name())){
					msg.textType = TextType.status.name(); 			//force status text
					preBroadcastAction(user, msg, false, false, false);
					broadcastMessage(msg);
				
				//broadcast default welcome and byebye
				/* -- has been disabled, for now only server is allowed to send it --
				}else if (dataType.equals(DataType.welcome.name())){
					String senderName = (String) msg.data.get("username");
					SocketMessage msgWelcome = makeServerStatusMessage(msgId, channelId, 
							(((senderName != null)? senderName : "???") + " (" + msg.sender + ") joined the chat"), 
							DataType.welcome, true);
			        broadcastMessage(msgWelcome);
			    */
			        
				//authenticate
				}else if (dataType.equals(DataType.authenticate.name())){
					//check credentials
					JSONObject credentials = (JSONObject) msg.data.get("credentials");
					if (credentials != null && !credentials.isEmpty()){
						//----- build auth. request ----
				    	JSONObject parameters = (JSONObject) msg.data.get("parameters");
				    	if (parameters != null && !parameters.isEmpty()){
					    	JSON.put(parameters, "KEY", credentials.get("userId") + ";" + credentials.get("pwd"));
				    	}else{
				    		parameters = JSON.make(
				    			"KEY", credentials.get("userId") + ";" + credentials.get("pwd"),
				    			"client", ConfigDefaults.defaultClientInfo
				    		);
				    	}
				    	RequestParameters params = new RequestPostParameters(parameters);
						//----------------------------
						Account userAccount = new Account();
						
						//AUTH. SUCCESS
						if (userAccount.authenticate(params)){
							
							//is assistant, thing or user? - we can add more here if required
							String userId = userAccount.getUserID();
							String deviceId = (String) msg.data.get("deviceId");
							Role role;
							if (userAccount.hasRole(Role.assistant.name())){
								role = Role.assistant;
							}else if (userAccount.hasRole(Role.thing.name())){
								role = Role.thing;
							}else{
								role = Role.user;
							}
							log.info("Authenticated: " + userId + ", roles: " + userAccount.getUserRoles() + ", deviceId: " + deviceId); 		//debug
							//System.out.println("Parameters: " + parameters); 		//debug
							
							//CREATE and STORE SocketUser
							String acceptedName = userAccount.getUserNameShort();
							SocketUser participant = new SocketUser(userSession, userId, acceptedName, role);
							participant.setDeviceId(deviceId);
							storeUser(participant);
							participant.setAuthenticated();
							
							SocketUserPool.removePendingSession(userSession);
							
							//CREATE and STORE private SocketChannel - or get it from pool
							if (sc == null){
								sc = SocketChannelPool.getChannel(userId);
							}
							if (sc == null){
								String channelKey = SocketChannelPool.createChannel(userId, userId, false);
								sc = SocketChannelPool.getChannel(userId);
								channelId = sc.getChannelId();
								//add user
								sc.addUser(participant, channelKey);
								//add default participants
								sc.addSystemDefaultAssistant();
								//... we can add more here coming from user account ...
							}else{
								channelId = sc.getChannelId();
							}
							participant.setActive();
							participant.setActiveChannel(sc.getChannelId());
							//assistants are omni-present in their active channels
							if (role.equals(Role.assistant)){
								participant.setOmnipresent();
							}
							
							JSONObject data = new JSONObject();
					        JSON.add(data, "dataType", DataType.joinChannel.name());
					        JSON.add(data, "channelId", sc.getChannelId());
					        JSON.add(data, "givenName", acceptedName);

					        SocketMessage msgUserName = new SocketMessage(channelId, SocketConfig.SERVERNAME, userId, data);
					        if (msgId != null) msgUserName.setMessageId(msgId);
					        preBroadcastAction(participant, msgUserName, true, false, false);
					        broadcastMessage(msgUserName, userSession);

					        //broadcast channel welcome and update userList to whole channel
					        SocketMessage msgListUpdate = makeServerStatusMessage("", channelId, (acceptedName + " (" + userId + ") joined the chat"), DataType.welcome, true);
					        preBroadcastAction(participant, msgListUpdate, false, false, false);
					        broadcastMessage(msgListUpdate);
						
						//AUTH. FAIL
						}else{
							SocketMessage msgLoginError = makeServerStatusMessage(msgId, "<auto>", "Login failed, credentials wrong or assistant not reachable", DataType.errorMessage, false);
							preBroadcastAction(user, msgLoginError, true, false, false);
							broadcastMessage(msgLoginError, userSession);
						}
					//AUTH. missing credentials to try
					}else{
						SocketMessage msgLoginError = makeServerStatusMessage(msgId, "<auto>", "Login failed, missing credentials", DataType.errorMessage, false);
						preBroadcastAction(user, msgLoginError, true, false, false);
						broadcastMessage(msgLoginError, userSession);
					}
					
				//join channel
				}else if (dataType.equals(DataType.joinChannel.name())){
					//System.out.println("JOIN CHANNEL: " + msg.data); 		//DEBUG
					JSONObject credentials = (JSONObject) msg.data.get("credentials");
					if (credentials != null && !credentials.isEmpty()){
						String newChannelId = (String) credentials.get("channelId");
						if (newChannelId != null && !newChannelId.isEmpty()){
							SocketChannel nsc = SocketChannelPool.getChannel(newChannelId);
							//channel exists?
							if (nsc != null){
								boolean isAllowed = false;
								
								//is member?
								if (nsc.isUserMemberOfChannel(user)){
									isAllowed = true;
								
								//not-member
								}else{
									if (nsc.isOpen()){
										nsc.addUser(user, "open");
										isAllowed = true;
									
									}else{
										//TODO: test check if user can register for a channel
										String newChannelKey = (String) credentials.get("channelKey");
										if (newChannelKey != null && !newChannelKey.isEmpty()){
											if (nsc.addUser(user, newChannelKey)){
												isAllowed = true;
											}
										}
									}
								}
								if (isAllowed){
									//broadcast old channel byebye
									String oldChannel = user.getActiveChannel();	//first get old channel ...
									user.setActiveChannel(nsc.getChannelId());		//... then remove user from channel
							        SocketMessage msgListUpdate1 = makeServerStatusMessage("", oldChannel, (user.getUserName() + " (" + user.getUserId() + ") left the channel (" + oldChannel + ")"), DataType.byebye, true);
							        preBroadcastAction(user, msgListUpdate1, false, false, true);
							        broadcastMessage(msgListUpdate1);
									
							        //confirm channel switch
							        JSONObject data = new JSONObject();
							        JSON.add(data, "dataType", DataType.joinChannel.name());
							        JSON.add(data, "channelId", nsc.getChannelId());
							        JSON.add(data, "givenName", user.getUserName());

							        SocketMessage msgJoinChannel = new SocketMessage("", SocketConfig.SERVERNAME, user.getUserId(), data);
							        //TODO: add channel users here? UI is usually expecting that ... but then it comes twice
							        msgJoinChannel.setUserList(SocketChannelPool.getChannel(nsc.getChannelId()).getActiveMembers(true));
							        if (msgId != null) msgJoinChannel.setMessageId(msgId);
							        preBroadcastAction(user, msgJoinChannel, true, false, false);
							        broadcastMessage(msgJoinChannel, userSession);
							        
							        //broadcast channel welcome and update userList
							        SocketMessage msgListUpdate2 = makeServerStatusMessage("", nsc.getChannelId(), (user.getUserName() + " (" + user.getUserId() + ") joined the channel (" + nsc.getChannelId() + ")"), DataType.welcome, true);
							        preBroadcastAction(user, msgListUpdate2, false, false, false);
							        broadcastMessage(msgListUpdate2);
								
								}else{
									//broadcast fail of channel join
									SocketMessage msgSwitchError = makeServerStatusMessage(msgId, "<auto>", "Channel join failed, are you allowed in this channel?", DataType.errorMessage, false);
									preBroadcastAction(user, msgSwitchError, true, false, false);
									broadcastMessage(msgSwitchError, userSession);									
								}
							}else{
								//broadcast fail - channel does not exist
								SocketMessage msgSwitchError = makeServerStatusMessage(msgId, "<auto>", "Channel join failed, channel does not exist!", DataType.errorMessage, false);
								preBroadcastAction(user, msgSwitchError, true, false, false);
								broadcastMessage(msgSwitchError, userSession);									
							}
						}
					}
				
			    //broadcast remote action - note: a remote action needs to be validated by an assistant (see checks above)
				}else if (dataType.equals(DataType.remoteAction.name())){
					String remoteUserId = (String) msg.data.get("user");
					String remoteMsgType = (String) msg.data.get("type");
					String action = (String) msg.data.get("action");
					//channelId, deviceId ...
					
					//rebuild message to stay clean (?)
					String receiver = remoteUserId;		//currently remote actions can only be sent to the user who triggered them
					
					SocketMessage remoteMsg = new SocketMessage(channelId, SocketConfig.SERVERNAME, receiver, 
							(remoteUserId + " sent remoteAction: " + remoteMsgType), TextType.status.name());
					remoteMsg.data = JSON.make("user", remoteUserId, "type", remoteMsgType, "action", action,
							"dataType", dataType);
					//remoteMsg.setUserList(getUserList());
					if (msgId != null) remoteMsg.setMessageId(msgId);
					
					//TODO: is this actually a single session broadcast?
					//preBroadcastAction(user, remoteMsg, true, false, true);
			        broadcastMessage(remoteMsg, user.getUserSession());
			        
				//unknown dataTypes
				}else{
					log.error(DateTime.getLogDate() + " ERROR - " + this.getClass().getName() + " - unhandled message dataType: " + dataType);
				}
			
			//all others are simply broadcasted
			}else if (isValidMessage){
				preBroadcastAction(user, msg, false, false, false);
				broadcastMessage(msg);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    //-------------- broadcasting ----------------
    
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
    public long getLastBroadcastTime(){
    	return timeOfLastBroadcast;
    }
    
    //Action to be executed pre-broadcast, depending on parameters of broadcast
    public void preBroadcastAction(SocketUser user, SocketMessage msg, boolean isSingleSession, boolean isAllSessions, boolean skipActiveCheck){
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
    @Override
    public void broadcastMessage(SocketMessage msg){
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
    @Override
    public void broadcastMessage(SocketMessage msg, String channelId){
    	//TODO: check channel "<auto>"
    	SocketChannel sc = SocketChannelPool.getChannel(channelId);
    	List<SocketUser> activeChannelUsers = sc.getActiveMembers(false); 		//TODO: this one is tricky, for normal messages it should be "false" 
    	broadcastMessage(msg, activeChannelUsers);
    }
    //Sends message to a list of active channel users
    public void broadcastMessage(SocketMessage msg, Collection<SocketUser> userList){
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
    				removeUser(su);
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
    				removeUser(u);
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
    @Override
    public void broadcastMessage(SocketMessage msg, Session session) {
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
    @Override
    public void broadcastMessage(JSONObject msg, Session session) {
    	//System.out.println(msg); 		//DEBUG
       	try {
            session.getRemote().sendString(msg.toJSONString());
            timeOfLastBroadcast = System.currentTimeMillis();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
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
