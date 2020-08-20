package net.b07z.sepia.websockets.common;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.websockets.server.SepiaClientPingHandler;
import net.b07z.sepia.websockets.server.SepiaClientPingHandler.PingRequest;

/**
 * User of the webSocketClient/Server
 * 
 * @author Florian Quirin
 *
 */
public class SocketUser {
	
	public final static String GUEST = "Guest";
	private static AtomicLong nextSessionId = new AtomicLong();
	
	private Session userSession;		//connection session
	private String userId;				//unique user id
	private String userName;			//self chosen name
	private Role role;					//role given by system
	
	private String deviceId = "";		//device id to distinguish between same accounts on different devices
	private long sessionId = 0; 		//random id to distinguish between same accounts in general
	
	private String activeChannelId;		//currently a user can only be active in one channel at the same time
	private boolean isOmnipresent = false;		//a user can be active in all channels at the same time
	
	private boolean isActive = false;			//when a user connects he is inactive until he broadcasts his welcome. Users can also be deactivated if multiple same accounts are used.
	private boolean isAuthenticated = false;	//a user can authenticate to use more services (e.g. assistant)
	
	private JSONObject info;			//all kinds of additional (dynamic) info - parts of it may be added to 'getUserListEntry'
	
	/**
	 * Create a new "user" (can also be an assistant or IoT device) 
	 * @param session - webSocket session
	 * @param id - unique userId
	 * @param name - name chosen by user (account settings)
	 * @param role - role given to user by system (account entry)
	 * @param deviceId - client device ID
	 */
	public SocketUser(Session session, String id, String name, Role role, String deviceId){
		this.userSession = session;
		this.userId = id;
		this.userName = name;
		this.role = role;
		this.deviceId = deviceId;
		this.sessionId = nextSessionId.incrementAndGet();
	}
	
	@Override
	public String toString(){
		return ("userId:" + userId + ",userName:" + userName + ",role:" + role.name());
	}
	
	public Session getUserSession(){
		return userSession;
	}
	
	public String getUserId(){
		return userId;
	}
	
	public String getUserName(){
		return userName;
	}
	/**
	 * Update the user name, but prevent confusion with guests by modifying anything that starts with 'GUEST'.
	 */
	public void updateUserName(String newName){
		if (newName.startsWith(GUEST)){
			newName = "No" + newName;
		}
		this.userName = newName;
	}
	
	/**
	 * Get name and id as entry for the user list
	 */
	public JSONObject getUserListEntry(){
		JSONObject entry = JSON.make(
			"name", userName, 
			"id", userId,
			"isActive", isActive, 
			"deviceId", deviceId, 
			"sessionId", sessionId
		);
		JSON.put(entry, "role", role.name());
		if (info != null){
			JSONObject infoJson = new JSONObject();
			//use this white-list of added info
			if (info.containsKey("clientInfo")){
				JSON.put(infoJson, "clientInfo", info.get("clientInfo"));
			}
			if (info.containsKey("lastPing")){
				JSON.put(infoJson, "lastPing", info.get("lastPing"));
			}
			if (info.containsKey("deviceLocalSite")){
				JSON.put(infoJson, "deviceLocalSite", info.get("deviceLocalSite"));
			}
			if (info.containsKey("deviceGlobalLocation")){
				JSON.put(infoJson, "deviceGlobalLocation", info.get("deviceGlobalLocation"));
			}
			if (!infoJson.isEmpty()){
				JSON.put(entry, "info", infoJson);
			}
		}
		return entry;
	}
	
	public String getActiveChannel(){
		return activeChannelId;
	}
	public void setActiveChannel(String channelId){
		activeChannelId = channelId;
	}
	public boolean isActiveInChannelOrOmnipresent(String channelId){
		if (this.isOmnipresent() || (this.getActiveChannel().equals(channelId) && this.isActive())){
			return true;
		}else{
			return false;
		}
	}
	
	public boolean isOmnipresent(){
		return isOmnipresent;
	}
	public void setOmnipresent(){
		this.isOmnipresent = true;
	}
	
	public Role getUserRole(){
		return role;
	}
	
	public String getDeviceId(){
		return deviceId;
	}
	public void setDeviceId(String deviceId){
		this.deviceId = deviceId;
	}
	
	public boolean isActive(){
		return isActive;
	}
	public void setActive(){
		this.isActive = true;
	}
	public void setInactive(){
		this.isActive = false;
	}

	public boolean isAuthenticated(){
		return isAuthenticated;
	}
	public void setAuthenticated(){
		isAuthenticated = true;
	}
	
	/**
	 * Get some more info about user or client device.
	 */
	public JSONObject getInfo(){
		return info;
	}
	/**
	 * Get a specific field from user or client device info.
	 */
	public Object getInfoEntryOrNull(String key){
		if (info != null){
			return info.get(key);
		}else{
			return null;
		}
	}
	/**
	 * Set a specific field of the user or client device info.
	 */
	public void setInfo(String key, Object value){
		if (info == null) info = new JSONObject();
		JSON.put(info, key, value);
	}
	
	//--- stuff called after authentication or close ---
	
	public void closeAllActivities(){
		//cancel alive-ping request if any
		PingRequest pr = (PingRequest) getInfoEntryOrNull("nextPingRequest");
		if (pr != null){
			pr.cancelScheduledPing();
		}
	}
	
	public void registerActivities(){
		//start alive-ping requests
		if (SocketConfig.useAlivePings && userSession != null && userSession.isOpen()){
			//the first ping is sent directly to test if the client supports the feature
			SepiaClientPingHandler.scheduleNextUserPing(userSession, 15000);
		}
	}
}
