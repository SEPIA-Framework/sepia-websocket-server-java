package net.b07z.sepia.websockets.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.Security;

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
	 * Check if channel pool has channel with certain ID.
	 * @param channelId - ID to check
	 * @return
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
	 * @return access key to share or null if channel already exists
	 * @throws Exception
	 */
	public static SocketChannel createChannel(String channelId, String owner, boolean isOpenChannel, String channelName) throws Exception{
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
		String key = "open";
		if (!isOpenChannel){
			key = Security.bytearrayToHexString(
					Security.getEncryptedPassword(
							Security.getRandomUUID().replaceAll("-", ""),
							Security.getRandomSalt(32),
							10, 64));
		}
		SocketChannel sc = new SocketChannel(channelId, key, owner, channelName);
		addChannel(sc);
		log.info("New channel has been created by '" + owner + "' with ID: " + channelId); 		//INFO
		
		//TODO: store channel
		System.out.println("Channel to store: " + sc.getJson()); 		//DEBUG
		
		return sc;
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
		channelPool.remove(sc.getChannelId());
		
		//TODO: delete channel
		System.out.println("Channel to delete: " + sc.getJson()); 		//DEBUG
		
		return true;
	}
	
	/**
	 * Get all channels owned by a specific user.
	 * @param userId - ID of owner
	 * @return List (can be empty)
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

}
