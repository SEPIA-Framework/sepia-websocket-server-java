package net.b07z.sepia.websockets.database;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.simple.JSONObject;

import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.server.SocketChannelPool;

/**
 * Class that implements {@link ChannelsDatabase} by using in-memory storage.
 * Basically it just points to {@link SocketChannelPool} methods and acts as test implementation for the interface.<br>
 * 
 * NOTE: Will loose all data when server closes or restarts.
 * 
 * @author Florian Quirin
 *
 */
public class ChannelsInMemoryDb implements ChannelsDatabase {

	public ChannelsInMemoryDb(){}

	@Override
	public boolean hasChannelWithId(String channelId) throws Exception {
		return SocketChannelPool.hasChannelId(channelId);
	}

	@Override
	public int storeChannel(SocketChannel socketChannel){
		SocketChannelPool.addChannel(socketChannel);
		return 0;
	}
	
	@Override
	public int updateChannel(String channelId, JSONObject updateData){
		return 0;		//NOTE: does nothing (intentionally)
	}
	
	@Override
	public Map<String, SocketChannel> getAllChannles(boolean includeOtherServers){
		return new ConcurrentHashMap<>(); 		//NOTE: return empty Map
	}

	@Override
	public SocketChannel getChannelWithId(String channelId){
		return SocketChannelPool.getChannel(channelId);
	}

	@Override
	public List<SocketChannel> getAllChannelsOwnedBy(String userId){
		return SocketChannelPool.getAllChannelsOwnedBy(userId);
	}

	@Override
	public int removeChannel(String channelId){
		try{
			SocketChannelPool.deleteChannel(getChannelWithId(channelId));
			return 0;
		}catch (Exception e){
			return 2;
		}
	}

	@Override
	public long removeAllChannelsOfOwner(String userId) {
		List<SocketChannel> channels = getAllChannelsOwnedBy(userId);
		if (channels == null){
			return -1;
		}else{
			for (SocketChannel sc : channels){
				int code = removeChannel(sc.getChannelId());
				if (code != 0){
					return -1;
				}
			}
		}
		return channels.size();
	}
}
