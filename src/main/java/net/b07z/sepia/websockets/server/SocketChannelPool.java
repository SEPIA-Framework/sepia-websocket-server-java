package net.b07z.sepia.websockets.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.Security;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.database.ChannelsDatabase;

/**
 * Handle different channels on same webSocketServer.
 * 
 * @author Florian Quirin
 *
 */
public class SocketChannelPool {
	
	static Logger log = LoggerFactory.getLogger(SocketChannelPool.class);
	
	private static Map<String, SocketChannel> channelPool = new ConcurrentHashMap<>();
	private static AtomicLong channelCounter = new AtomicLong(0); 
	
	/**
	 * Get a unique ID for a new channel.
	 */
	public static String getRandomUniqueChannelId() throws Exception {
		return Security.bytearrayToHexString(Security.getSha256(
				channelCounter.incrementAndGet() + "-" + SocketConfig.localName + "-" + System.currentTimeMillis() 
		));
	}
	
	/**
	 * Set a channel pool to start with. Usually loaded during server start.
	 * @param newChannelPool
	 */
	public static void setPool(Map<String, SocketChannel> newChannelPool){
		channelPool = newChannelPool;
	}
	
	/**
	 * Check if channel pool has channel with certain ID.
	 * @param channelId - ID to check
	 */
	public static boolean hasChannelId(String channelId) {
		return (getChannel(channelId) != null);
	}
	
	/**
	 * Create new channel and return access key.
	 * @param channelId - channel ID
	 * @param owner - creator and admin of the channel
	 * @param isOpenChannel - is the channel open for everybody?
	 * @param channelName - arbitrary channel name
	 * @param members - collection of initial members
	 * @param addAssistant - add an assistant like SEPIA to the channel? Note: open channels will have it in any case
	 * @return {@link SocketChannel} or null if channel already exists
	 * @throws Exception
	 */
	public static SocketChannel createChannel(String channelId, String owner, boolean isOpenChannel, String channelName,
			Collection<String> members, boolean addAssistant) throws Exception{
		//is channelId allowed?
		/* -- this restriction only applies to the API endpoint not the method itself --
		if (!channelId.equalsIgnoreCase(owner) && !owner.equalsIgnoreCase(SocketConfig.SERVERNAME)){
			log.info("Channel '" + channelId + "' is NOT ALLOWED! Must be equal to owner OR owner must be server."); 		//INFO
			return null;
		}
		*/
		//does channel already exist?
		if (hasChannelId(channelId)){
			log.info("Channel '" + channelId + "' already exists!"); 		//INFO
			return null;
		}
		//has server reach max. number of channels?
		if (channelPool.size() >= SocketConfig.maxChannelsPerServer){
			log.error("Server has reached MAXIMUM NUMBER OF CHANNELS.");	//ERROR
			return null;
		}
		String key = "open";
		if (!isOpenChannel){
			key = Security.bytearrayToHexString(
					Security.getEncryptedPassword(
							Security.getRandomUUID().replaceAll("-", ""),
							Security.getRandomSalt(32),
							10, 64));
		}
		SocketChannel sc = new SocketChannel(channelId, key, owner, channelName);
		String channelKey = sc.getChannelKey();
		addChannel(sc);
				
		//make sure the owner is also a member (except when it's the server)
		if (!owner.equals(SocketConfig.SERVERNAME)){
			members.add(owner);
		}
		
		//add members
		for (String s : members){
			sc.addUser(s, channelKey);
			//System.out.println("Member: " + s); 									//DEBUG
		}
		
		//add assistant
		if (addAssistant){
			sc.addSystemDefaultAssistant(); 	//Add SEPIA too
			//System.out.println("Member: " + SocketConfig.systemAssistantId); 		//DEBUG
		}
		
		log.info("New channel has been created by '" + owner + "' with ID: " + channelId); 		//INFO
		
		//store channel
		ChannelsDatabase channelsDb = SocketConfig.getDefaultChannelsDatabase();
		int resCode = channelsDb.storeChannel(sc);
		if (resCode != 0){
			log.error("Failed to store new channel with ID: " + channelId + " - Result code: " + resCode);
			//TODO: retry later
		}
		
		return sc;
	}
	
	/**
	 * Update channel with new member.
	 * @param sc - socket channel
	 * @param members
	 */
	public static boolean addMembersToChannel(SocketChannel sc, List<String> members){
		//update channel
		for (String m : members){
			sc.addUser(m, sc.getChannelKey());	//we should already have permission here, so we can use sc.getChannelKey()
		}
		String channelId = sc.getChannelId();
		ChannelsDatabase channelsDb = SocketConfig.getDefaultChannelsDatabase();
		int resCode = channelsDb.updateChannel(channelId, sc.getJson());
		if (resCode != 0){
			log.error("Failed to update channel with ID: " + channelId + " - Result code: " + resCode);
			//TODO: retry later
			return false;
		}else{
			return true;
		}
	}
	
	/**
	 * Add channel to pool.
	 */
	public static void addChannel(SocketChannel sc){
		channelPool.put(sc.getChannelId(), sc);
	}
	
	/**
	 * Get channel or null.
	 */
	public static SocketChannel getChannel(String channelId){
		//ChannelsDatabase channelsDb = SocketConfig.getDefaultChannelsDatabase();		//TODO: use?
		return channelPool.get(channelId);
	}
	
	/**
	 * Remove channel from pool.
	 */
	public static boolean deleteChannel(SocketChannel sc){
		String channelId = sc.getChannelId();
		channelPool.remove(channelId);
		
		//delete channel
		ChannelsDatabase channelsDb = SocketConfig.getDefaultChannelsDatabase();
		int resCode = channelsDb.removeChannel(channelId);
		if (resCode != 0){
			log.error("Failed to delete channel with ID: " + channelId + " - Result code: " + resCode);
			//TODO: retry later
		}
		
		//TODO: remove all pending missed messages for this channel
		
		return true;
	}
	
	/**
	 * Return all channel IDs known to this server.
	 */
	public static Set<String> getAllRegisteredChannelIds(){
		return channelPool.keySet();
	}
	
	/**
	 * Get all channels owned by a specific user.
	 * @param userId - ID of owner
	 * @return List (can be empty) or null (error)
	 */
	public static List<SocketChannel> getAllChannelsOwnedBy(String userId){
		//ChannelsDatabase channelsDb = SocketConfig.getDefaultChannelsDatabase();		//TODO: use?
		List<SocketChannel> channels = new ArrayList<>();
		for (String cId : channelPool.keySet()) {
			SocketChannel sc = channelPool.get(cId);
			if (sc.getOwner().equalsIgnoreCase(userId)) {
				channels.add(sc);
			}
		}
		return channels;
	}
	
	/**
	 * Get all channels that have a specific user as a member.
	 * @param userId - member ID to check
	 * @param includePublic - include public channels
	 * @return List (can be empty) or null (error)
	 */
	public static List<SocketChannel> getAllChannelsAvailableTo(String userId, boolean includePublic){
		//ChannelsDatabase channelsDb = SocketConfig.getDefaultChannelsDatabase();		//TODO: use?
		List<SocketChannel> channels = new ArrayList<>();
		for (String cId : channelPool.keySet()) {
			SocketChannel sc = channelPool.get(cId);
			if ((includePublic && sc.isOpen()) || sc.isUserMemberOfChannel(userId)) {
				channels.add(sc);
			}
		}
		return channels;
	}
	
	/**
	 * Convert list of channels to JSONArray for client.
	 * @param channels - list of channels
	 * @param receiverUserId - ID of receiver or null. If not null some fields are modified if its equal to channel owner (e.g. key).
	 * @return JSONArray expected to be compatible with client channel list
	 */
	public static JSONArray convertChannelListToClientArray(List<SocketChannel> channels, String receiverUserId){
		JSONArray channelsArray = new JSONArray();
		for (SocketChannel sc : channels){
			JSONObject channelJson = JSON.make(
					"id", sc.getChannelId(),
					"name", sc.getChannelName(),
					"owner", sc.getOwner(),
					"server", sc.getServerId(),
					"isOpen", sc.isOpen()
			);
			if (sc.getOwner().equals(receiverUserId)){
				JSON.put(channelJson, "key", sc.getChannelKey()); 		//add key so the owner can generate invite links 
			}
			JSON.add(channelsArray, channelJson);
		}
		return channelsArray;
	}

}
