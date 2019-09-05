package net.b07z.sepia.websockets.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.ThreadManager;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.database.ChatsDatabase;

/**
 * Takes care of storing messages for each channel and notifications for users that missed a channel message.
 * 
 * @author Florian Quirin
 *
 */
public class SocketChannelHistory {
	
	static Logger log = LoggerFactory.getLogger(SocketChannelHistory.class);
	
	private static Map<String, Set<String>> channelsWithMissedMessagesForEachUser = new ConcurrentHashMap<>();
	
	private static Map<String, ConcurrentLinkedQueue<JSONObject>> lastMessagesStoredForEachChannel = new ConcurrentHashMap<>();		//NOTE: this caches ALL messages (below threshold) of ALL channels
	private static Map<String, AtomicInteger> numMessagesForEachChannel = new ConcurrentHashMap<>(); 	//NOTE: we use this because the size() method is slow
	private static Map<String, Long> lastPolledMessageTimestampsForChannel = new ConcurrentHashMap<>();
	private static Set<String> channelsScheduledForCleanUp = new ConcurrentSkipListSet<>();
	
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
		//store in DB if changed
		if (channels.add(channelId)){
			//don't wait for result (put in new thread)
			Set<String> channelsNow = new HashSet<>(channels);
			Thread worker = new Thread(() -> {
				ChatsDatabase chatsDb = SocketConfig.getDefaultChatsDatabase();
				int resCode = chatsDb.updateChannelsWithMissedMessagesForUser(userId, channelsNow, false); 	//Note the 'false'
				if (resCode != 0){
					log.error("Failed to update channel user data for missed messages - user ID: " + userId + " - Result code: " + resCode);
					//TODO: retry later
				}
			});
			worker.start();
		}
	}
	
	/**
	 * Get all channels with missed messages for a user.
	 * @param userId - ID of user that (potentially) has missed messages
	 * @return set of channel IDs (can be empty) or null (error)
	 */
	public static Set<String> getAllChannelsWithMissedMassegesForUser(String userId){
		Set<String> channels = channelsWithMissedMessagesForEachUser.get(userId);
		if (channels == null){
			//create object first
			channels = new ConcurrentSkipListSet<String>();
			channelsWithMissedMessagesForEachUser.put(userId, channels);
			//check DB if there is any data
			ChatsDatabase chatsDb = SocketConfig.getDefaultChatsDatabase();
			JSONObject cwmmResult = chatsDb.getAllChannelsWithMissedMassegesForUser(userId);
			if (Is.notNullOrEmpty(cwmmResult)){
				JSONArray channelsToCheck = JSON.getJArray(cwmmResult, "checkChannels");
				if (Is.notNullOrEmpty(channelsToCheck)){
					for (Object co : channelsToCheck){
						channels.add((String) co);
					}
				}
			}
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
			
			//store in DB - don't wait for result (put in new thread)
			Set<String> channelsNow = new HashSet<>(channels);
			Thread worker = new Thread(() -> {
				ChatsDatabase chatsDb = SocketConfig.getDefaultChatsDatabase();
				int resCode = chatsDb.updateChannelsWithMissedMessagesForUser(userId, channelsNow, true); 	//Note the 'true'
				if (resCode != 0){
					log.error("Failed to update channel user data (remove) for missed messages - user ID: " + userId + " - Result code: " + resCode);
					//TODO: retry later
				}
			});
			worker.start();
		}
	}

	/**
	 * Clear the set of channels that are marked for missed messages for a specific user.
	 * @param userId - ID of user that (potentially) has missed messages
	 */
	public static void clearSetOfChannelsWithMissedMessages(String userId){
		channelsWithMissedMessagesForEachUser.remove(userId);
		
		//store in DB
		ChatsDatabase chatsDb = SocketConfig.getDefaultChatsDatabase();
		int resCode = chatsDb.updateChannelsWithMissedMessagesForUser(userId, new HashSet<>(), false); 	//'true' or 'false' ?? assuming 'false'
		if (resCode != 0){
			log.error("Failed to update channel user data (clear) for missed messages - user ID: " + userId + " - Result code: " + resCode);
			//TODO: retry later
		}
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
				//init
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
				JSONObject polledMsg = messagesQueue.poll();
				size.decrementAndGet();
				//prepare clean-up
				long lastPolledTS = JSON.getLongOrDefault(polledMsg, "timeUNIX", -1l) + 1l;		//NOTE: +1 to catch this message as well ;-)
				lastPolledMessageTimestampsForChannel.put(channelId, lastPolledTS);
				if (channelsScheduledForCleanUp.add(channelId)){
					scheduleChannelCleanUpIfRequired();
				}
			}
			
			//store in DB - don't wait for result
			Thread worker = new Thread(() -> {
				ChatsDatabase chatsDb = SocketConfig.getDefaultChatsDatabase();
				int resCode = chatsDb.storeChannelMessage(msg);
				if (resCode != 0){
					log.error("Failed to store channel message in DB - Result code: " + resCode);
					//TODO: retry later
				}
			});
			worker.start();
		}
	}
	
	/**
	 * Get info about all available channel histories.
	 * @return JSONArray with a JSONObject for each channel with a history 
	 */
	public static JSONArray getChannelHistoryInfo(){
		JSONArray info = new JSONArray();
		try{
			Set<String> channelsInCache = lastMessagesStoredForEachChannel.keySet();
			for (String channelId : channelsInCache){
				Queue<JSONObject> messages = lastMessagesStoredForEachChannel.get(channelId);
				int size = numMessagesForEachChannel.get(channelId).get();
				long lastPollTS = lastPolledMessageTimestampsForChannel.get(channelId);
				int N = messages.size();
				if (N != size){
					log.error("getChannelHistoryInfo - Inconsistency in channel with ID '" + channelId + "': size was " + N + " but should be " + size);
					size = N;
				}
				JSON.add(info, JSON.make(
						"channelId", channelId,
						"size", size,
						"lastPoll", lastPollTS
				));
			}
		}catch (Exception e){
			Debugger.printStackTrace(e, 3);
			log.error("getChannelHistoryInfo - Failed due to error: " + e.getMessage());
		}
		return info;
	}
	
	/**
	 * Get all messages cached for a certain channel as JSONArray.
	 * @param channelId - ID of channel
	 * @param filter - Map of filters like "notOlderThan" (long) 
	 * @return array of messages (can be empty) or null (error)
	 */
	public static JSONArray getChannelHistoryAsJson(String channelId, Map<String, Object> filter){
		ConcurrentLinkedQueue<JSONObject> messagesQueue = lastMessagesStoredForEachChannel.get(channelId);
		
		//init and try to load once
		if (messagesQueue == null){
			messagesQueue = new ConcurrentLinkedQueue<>();
			lastMessagesStoredForEachChannel.put(channelId, messagesQueue);
			
			//restore from DB
			if (SocketConfig.storeMessagesPerChannel > 0){
				ChatsDatabase chatsDb = SocketConfig.getDefaultChatsDatabase();
				Long notOlderThanUnixDB = lastPolledMessageTimestampsForChannel.get(channelId);
				if (notOlderThanUnixDB == null) notOlderThanUnixDB = 0l;
				List<SocketMessage> messagesOfChannel = chatsDb.getAllMessagesOfChannel(channelId, notOlderThanUnixDB);
				if (messagesOfChannel == null){
					//no messages - init caching variables empty
					numMessagesForEachChannel.put(channelId, new AtomicInteger(0));
					lastPolledMessageTimestampsForChannel.put(channelId, 0l);
				}else{
					//set caching variables
					Collections.sort(messagesOfChannel, new SocketMessage.SortByTimestampOldToNew());
					int N = messagesOfChannel.size();
					int skip = N - SocketConfig.storeMessagesPerChannel;
					if (skip > 0){
						//remove overflow elements
						messagesOfChannel.subList(0, skip).clear();
						N = messagesOfChannel.size();
						log.info("getChannelHistoryAsJson - skipped first " + skip + " messages of channel '" + channelId + "' to reduce size to " + N);
					}
					numMessagesForEachChannel.put(channelId, new AtomicInteger(N));
					if (N > 0){
						lastPolledMessageTimestampsForChannel.put(channelId, messagesOfChannel.get(0).timeStampUNIX - 5000);	//we set this to first msg TS - 5s
					}else{
						lastPolledMessageTimestampsForChannel.put(channelId, 0l);
					}
					//transfer to concurrent queue
					for (SocketMessage msg : messagesOfChannel){
						messagesQueue.add(msg.getJSON());
					}
				}
			}
		}
		//we filter again by user request
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
		JSONArray ja = new JSONArray();
		messagesQueue.forEach(socketMessage -> {
			if (notOlderThan == 0 || (JSON.getLongOrDefault(socketMessage, "timeUNIX", 0) >= notOlderThan)){
				JSON.add(ja, socketMessage); 			//TODO: filter content? (again)
			}
		});
		return ja;
	}
	
	/**
	 * Clean up history of specific channels that have been put on schedule.
	 */
	public static void cleanUpChannelsOnSchedule(){
		if (channelsScheduledForCleanUp.size() > 0){
			long tic = Debugger.tic();
			log.info("cleanUpChannelsOnSchedule - Starting scheduled clean-up...");  
			//copy and reset
			Set<String> cleanUpSetCopy = new HashSet<>(channelsScheduledForCleanUp);
			channelsScheduledForCleanUp = new ConcurrentSkipListSet<>();
			//clean-up ... we make consecutive calls (slower but not so stressful for the system compared to parallel stream etc.)  
			ChatsDatabase chatsDb = SocketConfig.getDefaultChatsDatabase();
			int removedAll = 0;
			for (String channelId : cleanUpSetCopy){
				Long lastPollTS = lastPolledMessageTimestampsForChannel.get(channelId);
				//this is definitely set ... I swear ^^
				if (lastPollTS != null){
					int removed = chatsDb.removeOldChannelMessages(channelId, lastPollTS);
					if (removed == -1){
						log.error("cleanUpChannelsOnSchedule - Unknown error for channel with ID: " + channelId);
					}else{
						removedAll += removed;
					}
				}
			}
			log.info("cleanUpChannelsOnSchedule - Done with clean-up, removed " + removedAll 
					+ " messages from " + cleanUpSetCopy.size() + " channels in " + Debugger.toc(tic) + "ms");
		}
	}
	/**
	 * Schedule next clean-up of channel history if not already scheduled.
	 */
	public static void scheduleChannelCleanUpIfRequired(){
		if (channelsScheduledForCleanUp.size() == 1){
			ThreadManager.scheduleTaskToRunOnceInBackground(SocketConfig.channelCleanUpScheduleDelay, () -> {
				cleanUpChannelsOnSchedule();
			});
			log.info("scheduleChannelCleanUp - Scheduled clean-up for UNIX time: " 
					+ (System.currentTimeMillis() + SocketConfig.channelCleanUpScheduleDelay));
		}
	}
	
	/**
	 * Get all channels with a history and remove everything that is older than the last poll timestamp for each channel.<br> 
	 * Use with caution! If many channels exist this might eat up some CPU time and memory.
	 * @return result info or null (error)
	 */
	public static JSONObject cleanUpOldChannelHistories(){
		try{
			long tic = Debugger.tic();
			log.info("cleanUpOldChannelHistories - Starting clean-up ...");
			JSONObject info = new JSONObject();
			JSONArray channelInfoArray = new JSONArray();
			Set<String> allChannels = SocketChannelPool.getAllRegisteredChannelIds();
			ChatsDatabase chatsDb = SocketConfig.getDefaultChatsDatabase();
			int errors = 0;
			for (String channelId : allChannels){
				int size = -1;
				Long lastPollTS = lastPolledMessageTimestampsForChannel.get(channelId);
				if (lastPollTS == null){
					//we need to load this then
					List<SocketMessage> messagesOfChannel = chatsDb.getAllMessagesOfChannel(channelId, 0l);
					if (messagesOfChannel != null){
						size = messagesOfChannel.size();
						//exceeds limit?
						if (size > SocketConfig.storeMessagesPerChannel){
							//sort and get timestamp of first message out of limit
							Collections.sort(messagesOfChannel, new SocketMessage.SortByTimestampOldToNew());
							lastPollTS = messagesOfChannel.get(size - SocketConfig.storeMessagesPerChannel).timeStampUNIX;
						}
					}
				}
				JSONObject channelInfo = JSON.make(
						"channelId", channelId
				);
				if (size != -1){
					JSON.put(channelInfo, "sizeBefore", size);
				}
				int removed = 0;
				if (lastPollTS != null){
					//call DB
					removed = chatsDb.removeOldChannelMessages(channelId, lastPollTS);
					if (removed == -1){
						log.error("cleanUpOldChannelHistories - Unknown error for channel with ID: " + channelId);
						errors++;
					}
					JSON.put(channelInfo, "lastPoll", lastPollTS);
				}
				JSON.put(channelInfo, "removed", removed);
				JSON.add(channelInfoArray, channelInfo);
			}
			long toc = Debugger.toc(tic);
			JSON.put(info, "errors", errors);
			JSON.put(info, "channelData", channelInfoArray);
			JSON.put(info, "took", toc);
			log.info("cleanUpOldChannelHistories - Clean-up done in " + toc + "ms");
			return info;
			
		}catch (Exception e){
			Debugger.printStackTrace(e, 3);
			log.error("cleanUpOldChannelHistories - Failed due to error: " + e.getMessage());
			return null;
		}
	}
}
