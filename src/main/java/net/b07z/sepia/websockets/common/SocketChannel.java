package net.b07z.sepia.websockets.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.Security;

/**
 * This class represents a default private channel on the webSocketServer for registered users.
 * 
 * @author Florian Quirin
 *
 */
public class SocketChannel {
	
	//Some fixed channel IDs (don't allow users to create them!)
	public static final String OPEN_WORLD = "openWorld";
	public static final String INFO = "info";
	public static final String UNKNOWN = "unknown";
	public static final List<String> systemChannels = new ArrayList<>();
	static {
		systemChannels.add(OPEN_WORLD);
		systemChannels.add(INFO);
		systemChannels.add(UNKNOWN);
	}
	
	private String channelId;		//ID of the channel (has to be unique)
	private String channelKey;		//password to access channel
	private String channelName;		//given name of channel (does not have to be unique)

	private String serverId;		//ID of server where this channel was created, aka server-local-name
	
	private String owner;			//admin of the channel
	private boolean isOpen = false;
	private Set<String> members;	//users allowed to be in this channel
	
	/**
	 * Create new channel. NOTE: server ID will be assigned automatically to local server name.
	 * @param id - unique channel ID
	 * @param key - security key
	 * @param owner - channel owner (user ID)
	 * @param channelName - given name of the channel (not unique)
	 */
	public SocketChannel(String id, String key, String owner, String channelName){
		this.channelId = id;
		this.channelKey = key;
		if (Is.notNullOrEmpty(channelName)){
			this.channelName = channelName;
		}else{
			this.channelName = "New Channel";
		}
		this.serverId = SocketConfig.localName;
		this.owner = owner;
		this.members = new ConcurrentSkipListSet<String>();
		if (key.equals("open")){
			this.isOpen = true;
			this.members.add(ConfigDefaults.defaultAssistantUserId);
		}
	}
	/**
	 * Import channel data from JSON object previously generated with {@link #getJson()}.
	 */
	public SocketChannel(JSONObject channelJson){
		this.channelId = JSON.getString(channelJson, "channel_id");
		this.channelKey = JSON.getString(channelJson, "channel_key");
		this.channelName = JSON.getString(channelJson, "channel_name");
		this.serverId = JSON.getString(channelJson, "server_id");
		this.isOpen = JSON.getBoolean(channelJson, "public");
		this.owner = JSON.getString(channelJson, "owner");
		this.members = new ConcurrentSkipListSet<String>();
		JSONArray membersArray = JSON.getJArray(channelJson, "members");
		if (membersArray != null){
			for (Object o : membersArray){
				this.members.add(o.toString());
			}
		}
		if (this.isOpen){
			//might already be in members but just to make sure ...
			this.members.add(ConfigDefaults.defaultAssistantUserId);
		}
	}
	
	/**
	 * Check access key for validity. An access key can be one of two things:<br>
	 * a) The internal access key of the channel<br>
	 * b) The hash of userId and internal access key<br>
	 * If you submit a userId the key will be checked against the hash (b) else it will be checked against
	 * the internal key (a).<br>
	 * Using the hash allows a channel owner to invite a specific user instead of giving him a key that everybody can use.
	 * @param key - channel access key or hash of user ID + channel key
	 * @param userId - ID of user to allow access or null
	 * @return
	 */
	public boolean checkUserOrChannelKey(String key, String userId){
		if (Is.notNullOrEmpty(userId)){
			try{
				String expectedHash = Security.bytearrayToHexString(Security.getSha256(userId + this.channelKey));
				if (expectedHash.equals(key)){
					return true;
				}else{
					return false;
				}
			}catch (Exception e){
				e.printStackTrace();
				return false;
			} 
		}else if (key.equals(this.channelKey)){
			return true;
		}else{
			return false;
		}
	}
	
	@Override
	public String toString(){
		return ("channelId:" + channelId + ",members-size:" + members.size());
	}
	
	public String getChannelId(){
		return channelId;
	}
	
	public String getChannelKey(){
		return channelKey;
	}
	
	public String getChannelName(){
		return channelName;
	}
	
	public String getServerId(){
		return serverId;
	}
	
	public String getOwner(){
		return owner;
	}
	public void setOwner(String userId){
		this.owner = userId;
	}
	
	public boolean isOpen(){
		return this.isOpen;
	}
	
	/**
	 * Add user to channel if channelKey fits.
	 * @param userId - user to add
	 * @param channelKey - channel access key
	 * @return true (user added)/false (not allowed)
	 */
	public boolean addUser(SocketUser user, String channelKey){
		return addUser(user.getUserId(), channelKey);
	}
	/**
	 * Add user to channel if channelKey fits.
	 * @param userId - user to add
	 * @param channelKey - channel access key
	 * @return true (user added)/false (not allowed)
	 */
	public boolean addUser(String userId, String channelKey){
		//check key
		if (checkUserOrChannelKey(channelKey, null)){		//NOTE: we expect internal access key here
			if (userId != null && !userId.isEmpty()){
				members.add(userId);
				return true;
			}else{
				return false;
			}
		}else{
			return false;
		}
	}
	
	public void addSystemDefaultAssistant(){
		members.add(ConfigDefaults.defaultAssistantUserId);
	}
	
	public boolean removeUser(SocketUser user, String channelKey){
		//check key
		if (channelKey.equals(this.channelKey)){
			String userId = user.getUserId();
			if (userId != null && !userId.isEmpty()){
				members.remove(user.getUserId());
				return true;
			}else{
				return false;
			}
		}else{
			return false;
		}
	}
	
	/**
	 * Return all online members of this channel. Note: this can return multiple SocketUsers with same ID (one for each device logged in). 
	 */
	public List<SocketUser> getAllOnlineMembers(){
		List<SocketUser> users = new ArrayList<>();
		for (String u : members){
			users.addAll(SocketUserPool.getAllUsersById(u));
		}
		return users;
	}
	
	/**
	 * Get a set of user IDs of all the users that are members of this channel.
	 */
	public Set<String> getAllRegisteredMembersById(){
		return members;
	}
	
	/**
	 * Get all members of a channel that have an active connection to the server and are currently in this channel.
	 * @param includeDeactivated - some users might have an active connection but their status is set to "inactive" (e.g. because they use the same account and deviceId). If they should be included set this to true.
	 */
	public List<SocketUser> getActiveMembers(boolean includeDeactivated){
		List<SocketUser> users = new ArrayList<>();
		for (String u : members){
			List<SocketUser> sul = SocketUserPool.getAllUsersById(u);
			if (sul != null){
				for (SocketUser asu : sul){
					if (includeDeactivated){
						if (asu.isOmnipresent() || asu.getActiveChannel().equals(this.channelId)){
							users.add(asu);
						}
					}else if (asu.isActiveInChannelOrOmnipresent(this.channelId)){
						users.add(asu);
					}
				}
			}
		}
		return users;
	}
	
	public boolean isUserMemberOfChannel(SocketUser user){
		return members.contains(user.getUserId());
		//return getAllMembers().contains(user);
	}
	public boolean isUserMemberOfChannel(String userId){
		return members.contains(userId);
	}
	
	/**
	 * Get channel data as JSON object. NOTE: this is meant for a database and usually needs to be filtered and converted for a client!
	 */
	@SuppressWarnings("unchecked")
	public JSONObject getJson(){
		JSONObject channel = new JSONObject();
		JSON.put(channel, "channel_id", channelId);
		JSON.put(channel, "channel_key", channelKey);
		JSON.put(channel, "channel_name", channelName);
		JSON.put(channel, "server_id", serverId);
		JSON.put(channel, "owner", owner);
		JSON.put(channel, "public", isOpen);
		JSONArray membersArray = new JSONArray();
		membersArray.addAll(members);
		JSON.put(channel, "members", membersArray);
		/*
		JSON.put(channel, "assistants", new JSONArray());	//not yet implemented
		JSON.put(channel, "info", new JSONObject());		//not yet implemented
		*/
		return channel;
	}
}
