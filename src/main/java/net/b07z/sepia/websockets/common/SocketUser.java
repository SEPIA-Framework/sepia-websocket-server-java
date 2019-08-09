package net.b07z.sepia.websockets.common;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.tools.JSON;

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
		return entry;
	}
	
	public String getActiveChannel(){
		return activeChannelId;
	}
	public void setActiveChannel(String channelId){
		activeChannelId = channelId;
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
	
}
