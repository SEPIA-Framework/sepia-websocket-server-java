package net.b07z.sepia.websockets.server;

import java.util.Set;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketMessage.DataType;

/**
 * Handle data update requests.
 * 
 * @author Florian Quirin
 */
public class SepiaUpdateDataHandler implements ServerMessageHandler {
	
	static Logger log = LoggerFactory.getLogger(SepiaUpdateDataHandler.class);
	
	SocketServer server;
	
	/**
	 * Create new handler for SEPIA messages.
	 */
	public SepiaUpdateDataHandler(SocketServer server){
		this.server = server;
	}

	@Override
	public void handle(Session userSession, SocketMessage msg) throws Exception {
		//Get user
		SocketUser user = server.getUserBySession(userSession);
		
		//Send data 
		//System.out.println(msg.data.toJSONString()); 				//DEBUG
		String updateData = JSON.getString(msg.data, "updateData");
		//JSONObject data = JSON.getJObject(msg.data, "data");
	
		//Missed channel messages set
		if (updateData.equals("missedChannelMessage")){
			Set<String> channels = SocketChannelHistory.getAllChannelsWithMissedMassegesForUser(user.getUserId());
			if (channels != null){
				JSONArray data = new JSONArray();
				for (String channelId : channels){
					JSON.add(data, JSON.make("channelId", channelId));
				}
				SocketMessage msgUpdateData = SepiaSocketBroadcaster.makeServerUpdateDataMessage(
		        		"missedChannelMessage", data
		        );
		        server.broadcastMessage(msgUpdateData, userSession);
			}
		
		//Unknown request
		}else{
			//return error message
	        SocketMessage errorMsg = SepiaSocketBroadcaster.makeServerStatusMessage(
	        		"", "", "Error in updateData: unknown request", DataType.errorMessage, false
	        );
	        server.broadcastMessage(errorMsg, userSession);
		}
	}

}
