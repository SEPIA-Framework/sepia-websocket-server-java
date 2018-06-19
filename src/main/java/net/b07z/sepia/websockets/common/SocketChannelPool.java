package net.b07z.sepia.websockets.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
	
private static Map<String, SocketChannel> channelPool = new ConcurrentHashMap<>();

	static Logger log = LoggerFactory.getLogger(SocketChannelPool.class);
	
	/**
	 * Create new channel and return access key.
	 * @param channelId - channel ID aka name
	 * @param owner - creator and admin of the channel
	 * @param isOpenChannel - is the channel open for everybody?
	 * @return access key to share or null if channel already exists
	 * @throws Exception
	 */
	public static String createChannel(String channelId, String owner, boolean isOpenChannel) throws Exception{
		//does channel already exist?
		if (getChannel(channelId) != null){
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
		SocketChannel sc = new SocketChannel(channelId, key, owner);
		addChannel(sc);
		log.info("New channel has been created: " + channelId); 		//INFO
		return key;
	}
	
	public static void addChannel(SocketChannel sc){
		channelPool.put(sc.getChannelId(), sc);
	}
	
	/**
	 * Get channel or null.
	 */
	public static SocketChannel getChannel(String channelId){
		return channelPool.get(channelId);
	}
	
	public static void deleteChannel(SocketChannel sc){
		channelPool.remove(sc.getChannelId());
	}

}
