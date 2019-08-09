package net.b07z.sepia.websockets.server;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketMessage.DataType;
import net.b07z.sepia.websockets.common.SocketMessage.TextType;

/**
 * Handle remote key requests sent to server form a SEPIA client.
 * 
 * @author Florian Quirin
 */
public class SepiaRemoteActionHandler implements ServerMessageHandler {
	
	static Logger log = LoggerFactory.getLogger(SepiaRemoteActionHandler.class);
	
	SocketServer server;
	
	/**
	 * Create new handler for SEPIA authentication messages.
	 */
	public SepiaRemoteActionHandler(SocketServer server){
		this.server = server;
	}

	@Override
	public void handle(Session userSession, SocketMessage msg) throws Exception {
		String remoteUserId = (String) msg.data.get("user");
		String targetDeviceId = (String) msg.data.get("targetDeviceId"); 	//TODO: can be "<auto>"
		String remoteMsgType = (String) msg.data.get("type");
		String action = (String) msg.data.get("action");
		//channelId, deviceId ...
		//TODO: we really should add deviceId here to redirect request to a specific device and only fallback to active user if ID not given
		
		//rebuild message to stay clean (?)
		String receiver = remoteUserId;		//currently remote actions can only be sent to the user who triggered them
		
		String channelId = msg.channelId; 		//NOTE: this can be modified by parent message handler, e.g. <auto> -> specific channel
		SocketMessage remoteMsg = new SocketMessage(channelId, 
				SocketConfig.SERVERNAME, SocketConfig.localName, 		//TODO: change this to msg.sender, msg.senderDeviceId ?? Or do we need this for auth. ?? 
				receiver, targetDeviceId,
				(remoteUserId + " sent remoteAction: " + remoteMsgType), TextType.status.name()
		);
		remoteMsg.data = JSON.make("user", remoteUserId, "type", remoteMsgType, "action", action,
				"dataType", DataType.remoteAction.name());
		//remoteMsg.setUserList(getUserList());
		if (msg.msgId != null) remoteMsg.setMessageId(msg.msgId);
		
		//TODO: is this actually a single session broadcast?
		//preBroadcastAction(user, remoteMsg, true, false, true);
        server.broadcastMessage(remoteMsg, userSession);
	}

}
