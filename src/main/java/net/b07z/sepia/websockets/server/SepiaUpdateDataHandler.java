package net.b07z.sepia.websockets.server;

import java.util.Set;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.Is;
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
			
		//userOrDeviceInfo
		}else if (updateData.equals("userOrDeviceInfo")){
			JSONObject data = JSON.getJObject(msg.data, "data");
			if (data != null){
				//we only store white-listed items - compare: 'SocketUser#getUserListEntry'
				if (data.containsKey("deviceLocalSite")){
					JSONObject dls = JSON.getJObject(data, "deviceLocalSite");
					//optimize memory
					if (Is.nullOrEmpty(JSON.getString(dls, "location"))){ 	//.location has to be set!
						user.setInfo("deviceLocalSite", null);
					}else{
						user.setInfo("deviceLocalSite", dls);
					}
				}
				if (data.containsKey("deviceGlobalLocation")){
					JSONObject dgl = JSON.getJObject(data, "deviceGlobalLocation");
					//optimize memory
					if (Is.nullOrEmpty(JSON.getString(dgl, "latitude"))){	//.latitude and .longitude have to be set!
						user.setInfo("deviceGlobalLocation", null);
					}else{
						user.setInfo("deviceGlobalLocation", dgl);
					}
				}
			}
		
		//Unknown request
		}else{
			log.info("Unknown request: " + updateData);
			//return error message
	        SocketMessage errorMsg = SepiaSocketBroadcaster.makeServerStatusMessage(
	        		"", "", "Error in updateData: unknown request", DataType.errorMessage, false
	        );
	        errorMsg.addData("errorType", SocketMessage.ErrorType.updateRequest.name());
			errorMsg.addData("errorCode", 501);
	        server.broadcastMessage(errorMsg, userSession);
		}
	}

}
