package net.b07z.sepia.websockets.database;

import java.util.List;
import java.util.Set;

import org.json.simple.JSONObject;

import net.b07z.sepia.websockets.common.SocketMessage;

/**
 * Read and write channel messages and user data (e.g. missed messages).
 * 
 * @author Florian Quirin
 *
 */
public interface ChatsDatabase {
	
	/**
	 * Update/create (overwrite) set with channels that should be checked for messages by user.
	 * @param userId - user who missed a message
	 * @param channelIds - set of channel IDs
	 * @param userReceivedNote - set this to true if you update the set because user checked the channel (and hopefully saw messages)
	 * @return result code: 0) all good 1) connection error 2) unknown error
	 */
	public int updateChannelsWithMissedMessagesForUser(String userId, Set<String> channelIds, boolean userReceivedNote);
	
	/**
	 * Return all channels that should be checked by user for missed messages.
	 * @param userId - user might have missed a message
	 * @return JSONObject with channels 'checkChannels' array (can be empty) and other user data (e.g. 'lastMissedMessage') or null (error)
	 */
	public JSONObject getAllChannelsWithMissedMassegesForUser(String userId);

	/**
	 * Store a "safe" socket message as JSON in DB.
	 * @param msg - {@link SocketMessage} in JSON format, prepared to be stored (filtered, e.g. removed credentials etc.)
	 * @return result code: 0) all good 1) connection error 2) unknown error
	 */
	public int storeChannelMessage(JSONObject msg);
	
	/**
	 * Get all stored messages of a channel optionally with range filter.
	 * @param channelId - ID of channel
	 * @param notOlderThanUNIX - get only messages that are not older than this UNIX timestamp (use 0 for no filter)
	 * @return list (can be empty) or null (error)
	 */
	public List<SocketMessage> getAllMessagesOfChannel(String channelId, long notOlderThanUNIX);
	
	/**
	 * Remove a bunch of messages from a channel that are older than a specific timestamp
	 * @param channelId - ID of channel
	 * @param olderThanUnix - older than UNIX time (ms)
	 * @return number of removed messages or -1 for error
	 */
	public int removeOldChannelMessages(String channelId, long olderThanUnix);
}
