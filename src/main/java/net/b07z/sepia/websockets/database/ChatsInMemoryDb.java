package net.b07z.sepia.websockets.database;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.simple.JSONObject;

import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.server.SocketChannelHistory;

/**
 * This is a dummy for the {@link ChatsDatabase} interface and will not store any data since this is handled by {@link SocketChannelHistory} already.<br>
 * 
 * NOTE: At some point we might want to convert {@link SocketChannelHistory} to implement the interface and remove this class.
 * 
 * @author Florian Quirin
 *
 */
public class ChatsInMemoryDb implements ChatsDatabase {

	@Override
	public int updateChannelsWithMissedMessagesForUser(String userId, Set<String> channelIds){
		return 0;
	}

	@Override
	public Set<String> getAllChannelsWithMissedMassegesForUser(String userId){
		return new HashSet<String>();
	}

	@Override
	public int storeChannelMessage(JSONObject msg){
		return 0;
	}

	@Override
	public int removeOldChannelMessages(String channelId, long olderThanUnix){
		return 0;
	}

	@Override
	public List<SocketMessage> getAllMessagesOfChannel(String channelId, long notOlderThanUNIX){
		return new ArrayList<>();
	}

}
