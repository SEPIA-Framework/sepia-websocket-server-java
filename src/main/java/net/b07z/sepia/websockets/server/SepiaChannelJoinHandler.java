package net.b07z.sepia.websockets.server;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketMessage.DataType;

/**
 * Handle channel join request sent to server form a SEPIA client.
 * 
 * @author Florian Quirin
 */
public class SepiaChannelJoinHandler implements ServerMessageHandler {
	
	static Logger log = LoggerFactory.getLogger(SepiaChannelJoinHandler.class);
	
	SocketServer server;
	
	/**
	 * Create new handler for SEPIA authentication messages.
	 */
	public SepiaChannelJoinHandler(SocketServer server){
		this.server = server;
	}

	@Override
	public void handle(Session userSession, SocketMessage msg) throws Exception {
		//System.out.println("JOIN CHANNEL: " + msg.data); 		//DEBUG
		SocketUser user = server.getUserBySession(userSession);
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
							//TODO: test check if user can register for a channel - NOTE: probably rarely used, usually a user would get access via auto-assign or share-link
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
						SocketChannel oldSc = SocketChannelPool.getChannel(oldChannel);
						if (oldSc != null){
					        SocketMessage msgListUpdate1 = SepiaSocketBroadcaster.makeServerStatusMessage(
					        		"", oldChannel, 
					        		(user.getUserName() + " (" + user.getUserId() + ") left the channel"),	// (" + oldChannel + ")" 
					        		DataType.byebye, true
					        );
					        server.broadcastMessage(user, msgListUpdate1);
						}
						
				        //confirm channel switch
				        JSONObject data = new JSONObject();
				        JSON.add(data, "dataType", DataType.joinChannel.name());
				        JSON.add(data, "channelId", nsc.getChannelId());
				        JSON.add(data, "channelName", nsc.getChannelName());
				        JSON.add(data, "givenName", user.getUserName());

				        SocketMessage msgJoinChannel = new SocketMessage("", SocketConfig.SERVERNAME, SocketConfig.localName, 
				        		user.getUserId(), user.getDeviceId(), 
				        		data);
				        //TODO: add channel users here? UI is usually expecting that ... but then it comes twice
				        msgJoinChannel.setUserList(SocketChannelPool.getChannel(nsc.getChannelId()).getActiveMembers(true));
				        if (msg.msgId != null) msgJoinChannel.setMessageId(msg.msgId);
				        server.broadcastMessage(msgJoinChannel, userSession);
				        
				        //broadcast channel welcome and update userList
				        SocketMessage msgListUpdate2 = SepiaSocketBroadcaster.makeServerStatusMessage(
				        		"", nsc.getChannelId(), 
				        		(user.getUserName() + " (" + user.getUserId() + ") joined the channel (" + nsc.getChannelName() + ")"), 
				        		DataType.welcome, true
				        );
				        server.broadcastMessage(user, msgListUpdate2);
					
					}else{
						//broadcast fail of channel join
						SocketMessage msgSwitchError = SepiaSocketBroadcaster.makeServerStatusMessage(
								msg.msgId, "<auto>", 
								"Channel join failed, are you allowed in this channel?", 
								DataType.errorMessage, false
						);
						server.broadcastMessage(msgSwitchError, userSession);									
					}
				}else{
					//broadcast fail - channel does not exist
					SocketMessage msgSwitchError = SepiaSocketBroadcaster.makeServerStatusMessage(
							msg.msgId, "<auto>", 
							"Channel join failed, channel does not exist!", 
							DataType.errorMessage, false
					);
					server.broadcastMessage(msgSwitchError, userSession);									
				}
			}
		}
	}

}
