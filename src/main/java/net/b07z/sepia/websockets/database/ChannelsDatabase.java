package net.b07z.sepia.websockets.database;

import java.util.List;

import net.b07z.sepia.websockets.common.SocketChannel;

public interface ChannelsDatabase {
	
	/**
	 * Check if channel with ID already exists.
	 * @param channelId - ID of channel
	 * @return
	 * @throws Exception
	 */
	public boolean hasChannelWithId(String channelId) throws Exception;
	
	/**
	 * Store {@link SocketChannel} in database.
	 * @param socketChannel - channel to store
	 * @return result code: 0) all good 1) connection error 2) unknown error
	 */
	public int storeChannel(SocketChannel socketChannel);
	
	/**
	 * Remove channel from database.
	 * @param channelId - ID of channel to be removed
	 * @return result code: 0) all good 1) connection error 2) unknown error
	 */
	public int removeChannel(String channelId);
	
	/**
	 * Remove all channels owned by user.
	 * @param userId - ID of use who owns the channels
	 * @return number of removed object or -1 for error
	 */
	public long removeAllChannelsOfOwner(String userId);
	
	/**
	 * Get {@link SocketChannel} by ID from database.  
	 * @param channelId - ID of channel to get
	 * @return channel or null
	 */
	public SocketChannel getChannelWithId(String channelId);
	
	/**
	 * Get all channels owned by a user.
	 * @param userId - ID of user
	 * @return List (can be empty) or null (some error)
	 */
	public List<SocketChannel> getAllChannelsOwnedBy(String userId);
}
