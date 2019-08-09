package net.b07z.sepia.websockets.server;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.server.RequestParameters;
import net.b07z.sepia.server.core.server.RequestPostParameters;
import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.websockets.client.SepiaSocketClient;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketChannelPool;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketUserPool;
import net.b07z.sepia.websockets.common.SocketMessage.DataType;

/**
 * Handle authentication request sent to server form a SEPIA client.
 * 
 * @author Florian Quirin
 */
public class SepiaAuthenticationHandler implements ServerMessageHandler {
	
	static Logger log = LoggerFactory.getLogger(SepiaAuthenticationHandler.class);
	
	SocketServer server;
	
	/**
	 * Create new handler for SEPIA authentication messages.
	 */
	public SepiaAuthenticationHandler(SocketServer server){
		this.server = server;
	}

	@Override
	public void handle(Session userSession, SocketMessage msg) throws Exception {
		//check credentials
		JSONObject credentials = (JSONObject) msg.data.get("credentials");
		if (credentials != null && !credentials.isEmpty()){
			//----- build auth. request ----
	    	JSONObject parameters = (JSONObject) msg.data.get("parameters");
	    	if (parameters != null && !parameters.isEmpty()){
		    	JSON.put(parameters, SepiaSocketClient.CREDENTIALS_KEY, credentials.get(SepiaSocketClient.CREDENTIALS_USER_ID) + ";" + credentials.get(SepiaSocketClient.CREDENTIALS_PASSWORD));
	    	}else{
	    		parameters = JSON.make(
	    			SepiaSocketClient.CREDENTIALS_KEY, credentials.get(SepiaSocketClient.CREDENTIALS_USER_ID) + ";" + credentials.get(SepiaSocketClient.CREDENTIALS_PASSWORD),
	    			SepiaSocketClient.PARAMETERS_CLIENT, ConfigDefaults.defaultClientInfo
	    		);
	    	}
	    	RequestParameters params = new RequestPostParameters(parameters);
			//----------------------------
			Account userAccount = new Account();
			
			//AUTH. SUCCESS
			if (userAccount.authenticate(params)){
				
				//is assistant, thing or user? - we can add more here if required
				String userId = userAccount.getUserID();
				String deviceId = Is.notNullOrEmpty(msg.senderDeviceId)? 
						msg.senderDeviceId : ((Is.notNullOrEmpty(parameters))? JSON.getString(parameters, SepiaSocketClient.PARAMETERS_DEVICE_ID) : null); 
				if (Is.nullOrEmpty(deviceId)){
					deviceId = (String) msg.data.get("deviceId");		//Some clients might use this "extra" entry
				}
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
				SocketUser participant = new SocketUser(userSession, userId, acceptedName, role, deviceId);
				//participant.setDeviceId(deviceId);
				server.storeUser(participant);
				participant.setAuthenticated();
				
				SocketUserPool.removePendingSession(userSession);
				
				//CREATE and STORE private SocketChannel - or get it from pool
				SocketChannel sc = SocketChannelPool.getChannel(userId);
				String channelId = null;
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

		        SocketMessage msgUserName = new SocketMessage(channelId, SocketConfig.SERVERNAME, SocketConfig.localName, userId, deviceId, data);
		        if (msg.msgId != null) msgUserName.setMessageId(msg.msgId);
		        server.broadcastMessage(msgUserName, userSession);

		        //broadcast channel welcome and update userList to whole channel
		        SocketMessage msgListUpdate = SepiaSocketBroadcaster.makeServerStatusMessage(
		        		"", channelId, (acceptedName + " (" + userId + ") joined the chat"), DataType.welcome, true
		        );
		        server.broadcastMessage(participant, msgListUpdate);
			
			//AUTH. FAIL
			}else{
				SocketMessage msgLoginError = SepiaSocketBroadcaster.makeServerStatusMessage(
						msg.msgId, "<auto>", "Login failed, credentials wrong or assistant not reachable", DataType.errorMessage, false
				);
				server.broadcastMessage(msgLoginError, userSession);
			}
		//AUTH. missing credentials to try
		}else{
			SocketMessage msgLoginError = SepiaSocketBroadcaster.makeServerStatusMessage(
					msg.msgId, "<auto>", "Login failed, missing credentials", DataType.errorMessage, false
			);
			server.broadcastMessage(msgLoginError, userSession);
		}
	}

}
