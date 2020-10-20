package net.b07z.sepia.websockets.server;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketUserPool;
import net.b07z.sepia.websockets.common.SocketMessage.DataType;

/**
 * Handle remote action requests sent to server REST API form a SEPIA client.<br>
 * <br>
 * NOTE: Currently remote action requests are only allowed when sent via REST API 
 * endpoint and then converted to an assistant socket message. 
 * 
 * @author Florian Quirin
 */
public class SepiaRemoteActionHandler implements ServerMessageHandler {
	
	static Logger log = LoggerFactory.getLogger(SepiaRemoteActionHandler.class);
	
	SocketServer server;
	
	/**
	 * Create new handler for SEPIA messages.
	 */
	public SepiaRemoteActionHandler(SocketServer server){
		this.server = server;
	}

	@Override
	public void handle(Session userSession, SocketMessage msg) throws Exception {
		//NOTE: userSession is the session of the assistant NOT the user since it is the mediator between the REST API and socket message
		//Currently remote actions can only be sent to the user who triggered them
		List<SocketUser> users = findRemoteTargetSocketUsers(msg);
		if (users == null || users.isEmpty()){
			//log.info("SepiaRemoteActionHandler: Could not find target user. Message will not be sent."); 		//debug
			return;
		}
		String remoteMsgType = (String) msg.data.get("type");
		String action = (String) msg.data.get("action");
		
		//build new message - we send this as server message
		for (SocketUser user : users){
			//debug:
			/*
			System.out.println("RemoteUserId: " + user.getUserId());
			System.out.println("TargetChannel: " + user.getActiveChannel());
			System.out.println("TargetDevice: " + user.getDeviceId());
			*/
			String receiver = user.getUserId();		//the user who triggered the action via REST API from any device and receives it via socket message on target client
			String statusText = (receiver + " sent remoteAction: " + remoteMsgType); 
			SocketMessage remoteMsg = SepiaSocketBroadcaster.makeServerStatusMessage(null, 
					user.getActiveChannel(), 
					statusText, 
					DataType.remoteAction, false
			);
			remoteMsg.addData("user", receiver);
			remoteMsg.addData("type", remoteMsgType);
			remoteMsg.addData("action", action);
			
			//broadcast to target user session
	        server.broadcastMessage(remoteMsg, user.getUserSession());
		}
	}
	
	//--------------------
	
	/**
	 * Find the targeted user for the remote action by checking the message data.<br>
	 * Note: If target device ID and/or channel ID are set to 'auto' (and device ID is not 'all') the first result that fits both is taken.
	 * @return list of matching users (can be empty)
	 */
	public static List<SocketUser> findRemoteTargetSocketUsers(SocketMessage msg){
		//the actual user information is in the data
		String remoteUserId = (String) msg.data.get("remoteUserId");
		String targetDeviceId = (String) msg.data.get("targetDeviceId");
		String targetChannelId = (String) msg.data.get("targetChannelId");
		String skipDeviceId = (String) msg.data.get("skipDeviceId");
		boolean hasSkipDeviceId = Is.notNullOrEmpty(skipDeviceId);
		boolean channelIdIsAuto = (targetChannelId == null || targetChannelId.isEmpty() || targetChannelId.equals("<auto>"));
		boolean targetDeviceIsAuto = (targetDeviceId == null || targetDeviceId.isEmpty() || targetDeviceId.equals("<auto>"));
		boolean targetDeviceIsAll = (targetDeviceId != null && targetDeviceId.equals("<all>"));
		
		List<SocketUser> users = new ArrayList<>();
		if (remoteUserId != null && !remoteUserId.isEmpty()){
			//one match - the first active user found in any channel and device 
			if (channelIdIsAuto && targetDeviceIsAuto && !hasSkipDeviceId){
				SocketUser user = SocketUserPool.getActiveUserById(remoteUserId);
				if (user != null){
					users.add(user);
					return users;
				}
			}
			//one or many matches - find matching sessions
			List<SocketUser> possibleRemoteUsers = SocketUserPool.getAllUsersById(remoteUserId);
			boolean correctDevice = false;
			boolean correctChannel = false;
			for (SocketUser su : possibleRemoteUsers){
				String thisDeviceId = su.getDeviceId();
				correctDevice = (targetDeviceIsAll || targetDeviceIsAuto || (targetDeviceId.equals(thisDeviceId)));
				if (correctDevice && hasSkipDeviceId && skipDeviceId.equals(thisDeviceId)){
					correctDevice = false;
				}
				correctChannel = (channelIdIsAuto || (targetChannelId.equals(su.getActiveChannel())));
				if (correctDevice && correctChannel){
					users.add(su);
					if (!targetDeviceIsAll){
						return users;
					}
				}
			}
		}
		return users;
	}

}
