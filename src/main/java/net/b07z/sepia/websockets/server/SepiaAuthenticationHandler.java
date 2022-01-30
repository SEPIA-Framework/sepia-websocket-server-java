package net.b07z.sepia.websockets.server;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONArray;
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
import net.b07z.sepia.server.core.users.SharedAccessItem;
import net.b07z.sepia.websockets.client.SepiaSocketClient;
import net.b07z.sepia.websockets.common.SocketChannel;
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
				String clientInfo = JSON.getString(parameters, "client");
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
				Map<String, List<SharedAccessItem>> sharedAccess = userAccount.getSharedAccess();
				log.info("Authenticated: " + userId + ", roles: " + userAccount.getUserRoles() + ", deviceId: " + deviceId); 		//debug
				//System.out.println("Parameters: " + parameters); 		//debug
				
				//CREATE and STORE SocketUser
				String acceptedName = userAccount.getUserNameShort();
				SocketUser participant = new SocketUser(userSession, userId, acceptedName, role, deviceId);
				if (clientInfo != null){
					participant.setInfo("clientInfo", clientInfo);
				}
				//participant.setDeviceId(deviceId);
				if (sharedAccess != null){
					participant.setSharedAccess(sharedAccess);
				}
				server.storeUser(participant);
				participant.setAuthenticated();
				
				SocketUserPool.removePendingSession(userSession);
				
				//CREATE and STORE private SocketChannel - or get it from pool
				SocketChannel sc = SocketChannelPool.getChannel(userId);
				String channelId = null;
				if (sc == null){
					String channelName = "<assistant_name>";
					Set<String> members = new HashSet<String>(); 		//we can leave this empty, it will auto-add the owner
					boolean addAssistant = true;
					sc = SocketChannelPool.createChannel(userId, userId, false, channelName, members, addAssistant);
					channelId = sc.getChannelId();
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
				
				//broadcast channel-join event
				JSONObject data = new JSONObject();
		        JSON.add(data, "dataType", DataType.joinChannel.name());
		        JSON.add(data, "channelId", sc.getChannelId());
		        JSON.add(data, "channelName", sc.getChannelName());
		        JSON.add(data, "givenName", acceptedName);

		        SocketMessage msgUserName = new SocketMessage(channelId, SocketConfig.SERVERNAME, SocketConfig.localName, userId, deviceId, data);
		        if (msg.msgId != null) msgUserName.setMessageId(msg.msgId);
		        server.broadcastMessage(msgUserName, userSession);
		        
		        //broadcast request to update channel data
		        boolean includePublic = true;
		        List<SocketChannel> channels = SocketChannelPool.getAllChannelsAvailableTo(userId, includePublic);
		        JSONArray channelsArray = null;
		        if (channels != null){
		        	channelsArray = SocketChannelPool.convertChannelListToClientArray(channels, userId);
		        }
		        SocketMessage msgUpdateData = SepiaSocketBroadcaster.makeServerUpdateDataMessage(
		        		"availableChannels", channelsArray
		        );
		        server.broadcastMessage(msgUpdateData, userSession);

		        //broadcast channel welcome and update userList to whole channel
		        SocketMessage msgListUpdate = SepiaSocketBroadcaster.makeServerStatusMessage(
		        		"", channelId, (acceptedName + " (" + userId + ") joined the chat"), DataType.welcome, true
		        );
		        server.broadcastMessage(participant, msgListUpdate);
			
			//AUTH. FAIL
			}else{
				int authErrorCode = userAccount.getAuthErrorCode();
				SocketMessage msgLoginError;
				if (authErrorCode == 2){
					//Server reached but login failed 
					msgLoginError = SepiaSocketBroadcaster.makeServerStatusMessage(
							msg.msgId, "<auto>", "Login failed, credentials wrong or token expired (401)", DataType.errorMessage, false
					);
					msgLoginError.addData("errorType", SocketMessage.ErrorType.authentication.name());
					msgLoginError.addData("errorCode", 401);
				}else if (authErrorCode == 10){
					//Server reached but login temporarily blocked 
					msgLoginError = SepiaSocketBroadcaster.makeServerStatusMessage(
							msg.msgId, "<auto>", "Login temporarily blocked due to too many failed requests (429)", DataType.errorMessage, false
					);
					msgLoginError.addData("errorType", SocketMessage.ErrorType.authentication.name());
					msgLoginError.addData("errorCode", 429);
				}else{
					//Other error
					msgLoginError = SepiaSocketBroadcaster.makeServerStatusMessage(
							msg.msgId, "<auto>", "Login failed, assistant not reachable or unknown error (500)", DataType.errorMessage, false
					);
					msgLoginError.addData("errorType", SocketMessage.ErrorType.authentication.name());
					msgLoginError.addData("errorCode", 500);
					msgLoginError.addData("returnCode", authErrorCode);
				}
				server.broadcastMessage(msgLoginError, userSession);
			}
		//AUTH. missing credentials to try
		}else{
			SocketMessage msgLoginError = SepiaSocketBroadcaster.makeServerStatusMessage(
					msg.msgId, "<auto>", "Login failed, missing credentials (401)", DataType.errorMessage, false
			);
			server.broadcastMessage(msgLoginError, userSession);
		}
	}

}
