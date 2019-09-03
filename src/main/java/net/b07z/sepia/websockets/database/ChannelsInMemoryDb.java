package net.b07z.sepia.websockets.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.JSONObject;

import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.server.SocketChannelPool;

/**
 * Dummy class that implements {@link ChannelsDatabase} but returns no data since {@link SocketChannelPool} is already handling everything in-memory.
 * This just acts as a test implementation and blind plug (prevents any database access).<br>
 * 
 * NOTE: At some point we might want to convert {@link SocketChannelPool} to implement the interface and remove this class.
 * 
 * @author Florian Quirin
 *
 */
public class ChannelsInMemoryDb implements ChannelsDatabase {

	public ChannelsInMemoryDb(){}

	@Override
	public boolean hasChannelWithId(String channelId) throws Exception {
		return false;
	}

	@Override
	public int storeChannel(SocketChannel socketChannel){
		return 0;
	}
	
	@Override
	public int updateChannel(String channelId, JSONObject updateData){
		return 0;
	}
	
	@Override
	public Map<String, SocketChannel> getAllChannles(boolean includeOtherServers){
		return new ConcurrentHashMap<>(); 		//NOTE: return empty Map
	}

	@Override
	public SocketChannel getChannelWithId(String channelId){
		return null;
	}

	@Override
	public List<SocketChannel> getAllChannelsOwnedBy(String userId){
		return new ArrayList<SocketChannel>();	//NOTE: return empty List (not thread-safe)
	}

	@Override
	public int removeChannel(String channelId){
		return 0;
	}

	@Override
	public long removeAllChannelsOfOwner(String userId) {
		return 0;
	}
}
