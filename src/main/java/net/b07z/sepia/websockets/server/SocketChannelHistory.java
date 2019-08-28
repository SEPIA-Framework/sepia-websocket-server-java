package net.b07z.sepia.websockets.server;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;

/**
 * Takes care of storing messages for each channel and notifications for users that missed a channel message.
 * 
 * @author Florian Quirin
 *
 */
public class SocketChannelHistory {
	
	//TODO: make use of channel content database
	
	private static Map<String, Set<String>> channelsWithMissedMessagesForEachUser = new ConcurrentHashMap<>();
	
	private static Map<String, ConcurrentLinkedQueue<JSONObject>> lastMessagesStoredForEachChannel = new ConcurrentHashMap<>();
	private static Map<String, AtomicInteger> numMessagesForEachChannel = new ConcurrentHashMap<>(); 	//NOTE: we use this because the size() method is slow
	
	//--- Methods for handling channels of specific users that might have missed messages ---
	
	/**
	 * Add a channel to the set of channels that mark missed messages for a specific user.
	 * @param userId - ID of user that missed a message
	 * @param channelId - ID of channel with a missed message 
	 */
	public static void addChannelWithMissedMessagesForUser(String userId, String channelId){
		Set<String> channels = channelsWithMissedMessagesForEachUser.get(userId);
		if (channels == null){
			channels = new ConcurrentSkipListSet<String>();
			channelsWithMissedMessagesForEachUser.put(userId, channels);
		}
		channels.add(channelId);
	}
	
	/**
	 * Get all channels with missed messages for a user.
	 * @param userId - ID of user that (potentially) has missed messages
	 * @return set of channel IDs (can be empty) or null (error)
	 */
	public static Set<String> getAllChannelsWithMissedMassegesForUser(String userId){
		Set<String> channels = channelsWithMissedMessagesForEachUser.get(userId);
		if (channels == null){
			channels = new ConcurrentSkipListSet<String>();
			channelsWithMissedMessagesForEachUser.put(userId, channels);
			return channels;
		}else{
			return channels;
		}
	}
	
	/**
	 * Remove a channel from the set of channels that are marked for missed messages.
	 * @param userId - ID of user that (potentially) has missed messages
	 * @param channelId - ID of channel to remove
	 */
	public static void removeChannelFromMissedMessagesSet(String userId, String channelId){
		Set<String> channels = channelsWithMissedMessagesForEachUser.get(userId);
		if (channels != null){
			channels.remove(channelId);
		}
	}

	/**
	 * Clear the set of channels that are marked for missed messages for a specific user.
	 * @param userId - ID of user that (potentially) has missed messages
	 */
	public static void clearSetOfChannelsWithMissedMessages(String userId){
		channelsWithMissedMessagesForEachUser.remove(userId);
	}
	
	//--- Methods for handling channels content ---
	
	/**
	 * Store a {@link SocketMessage} for a {@SocketChannel} (via channel ID) as JSON and drop the oldest message when the queue is full.<br>
	 * NOTE: some content of the message (e.g. credentials) will be removed for security reasons.
	 * @param channelId - ID of channel
	 * @param socketMessage - message to store
	 */
	public static void addMessageToChannelHistory(String channelId, SocketMessage socketMessage){
		if (SocketConfig.storeMessagesPerChannel > 0){
			ConcurrentLinkedQueue<JSONObject> messagesQueue = lastMessagesStoredForEachChannel.get(channelId);
			AtomicInteger size = numMessagesForEachChannel.get(channelId);
			if (messagesQueue == null){
				messagesQueue = new ConcurrentLinkedQueue<>();
				lastMessagesStoredForEachChannel.put(channelId, messagesQueue);
				size = new AtomicInteger(0);
				numMessagesForEachChannel.put(channelId, size);
			}
			//make safe
			JSONObject msg = SepiaSocketBroadcaster.makeSafeMessage(socketMessage);
			//add
			messagesQueue.add(msg);
			//remove oldest
			if (size.incrementAndGet() > SocketConfig.storeMessagesPerChannel){
				messagesQueue.poll();
			}
		}
	}
	
	/**
	 * Get all messages cached for a certain channel as JSONArray.
	 * @param channelId - ID of channel
	 * @param filter - Map of filters like "notOlderThan" (long) 
	 * @return array of messages (can be empty) or null (error)
	 */
	public static JSONArray getChannelHistoryAsJson(String channelId, Map<String, Object> filter){
		ConcurrentLinkedQueue<JSONObject> messagesQueue = lastMessagesStoredForEachChannel.get(channelId);
		final long notOlderThan;
		if (filter != null){
			if (filter.containsKey("notOlderThan")){
				notOlderThan = (long) filter.get("notOlderThan");
			}else{
				notOlderThan = 0;
			}
		}else{
			notOlderThan = 0;
		}
		if (messagesQueue == null){
			return new JSONArray();
			//NOTE: we do this to distinguish error and "real" empty queue later
		}else{
			JSONArray ja = new JSONArray();
			messagesQueue.forEach(socketMessage -> {
				if (notOlderThan == 0 || (JSON.getLongOrDefault(socketMessage, "timeUNIX", 0) >= notOlderThan)){
					JSON.add(ja, socketMessage); 			//TODO: filter content? (again)
				}
			});
			return ja;
		}
	}
}
