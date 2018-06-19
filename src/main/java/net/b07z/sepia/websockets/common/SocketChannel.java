package net.b07z.sepia.websockets.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import net.b07z.sepia.server.core.server.ConfigDefaults;

/**
 * This class represents a default private channel on the webSocketServer for registered users.
 * 
 * @author Florian Quirin
 *
 */
public class SocketChannel {
	
	private String channelId;		//name of the channel
	private String channelKey;		//password to access channel
	
	private String owner;			//admin of the channel
	private boolean isOpen = false;
	private Set<String> members;	//users allowed to be in this channel
	
	public SocketChannel(String id, String key, String owner){
		this.channelId = id;
		this.channelKey = key;
		this.members = new ConcurrentSkipListSet<String>();
		if (key.equals("open")){
			this.isOpen = true;
			this.members.add(ConfigDefaults.defaultAssistantUserId);
		}
	}
	
	@Override
	public String toString(){
		return ("channelId:" + channelId + ",members-size:" + members.size());
	}
	
	public String getChannelId(){
		return channelId;
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
	
	public boolean addUser(SocketUser user, String channelKey){
		return addUser(user.getUserId(), channelKey);
	}
	public boolean addUser(String userId, String channelKey){
		//check key
		if (channelKey.equals(this.channelKey)){
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
	
	public List<SocketUser> getAllMembers(){
		List<SocketUser> users = new ArrayList<>();
		for (String u : members){
			users.addAll(SocketUserPool.getAllUsersById(u));
		}
		return users;
	}
	
	/**
	 * Get all members of a channel that have an active connection to the server and are currently in this channel.
	 * @param includeDeactivated - some users might have an active connection but their status is set to "inactive" (e.g. because they use the same account). If they should still receive status updates set this true.
	 */
	public List<SocketUser> getActiveMembers(boolean includeDeactivated){
		List<SocketUser> users = new ArrayList<>();
		for (String u : members){
			List<SocketUser> sul = SocketUserPool.getAllUsersById(u);
			if (sul != null){
				for (SocketUser asu : sul){
					if (asu.isOmnipresent() || asu.getActiveChannel().equals(this.channelId)){
						if (includeDeactivated || asu.isActive()){
							users.add(asu);
						}
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
}
