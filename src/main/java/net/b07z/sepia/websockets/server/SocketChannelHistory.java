package net.b07z.sepia.websockets.server;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import net.b07z.sepia.websockets.common.SocketMessage;

/**
 * Takes care of storing messages for each channel and notifications for users that missed a channel message.
 * 
 * @author Florian Quirin
 *
 */
public class SocketChannelHistory {
	
	private static Map<String, Set<String>> channelsWithMissedMessagesForEachUser = new ConcurrentHashMap<>();
	private static Map<String, List<SocketMessage>> lastMessagesStoredForEachChannel = new ConcurrentHashMap<>();
	
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
	
	
}
