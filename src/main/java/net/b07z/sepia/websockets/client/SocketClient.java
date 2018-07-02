package net.b07z.sepia.websockets.client;
import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONArray;

import net.b07z.sepia.websockets.common.SocketMessage;

/**
 * Interface for client socket.
 * 
 * @author Florian Quirin
 *
 */
public interface SocketClient {
	
	/**
	 * How many threads are active for data processing right now?
	 */
	public int getActiveThreads();
	/**
	 * How many threads were active at the same time at most?
	 */
	public int getMaxRegisteredThreads();
	/**
	 * Get some user readable statistics about this socket client (formatted in HTML).
	 */
	public String getStats();
	
	/**
	 * Wait a certain time for connection. 
	 * @param wait - maximum wait time in ms
	 * @return true for connection, false for timeout
	 * @throws InterruptedException
	 */
	public boolean awaitConnection(long wait) throws InterruptedException;
	
	/**
	 * Wait a certain time (typically Long.MAX_VALUE) for close event sent by server or triggered manually. 
	 * @param wait - maximum wait time in ms
	 * @return true for close, false for timeout
	 * @throws InterruptedException
	 */
	public boolean awaitClose(long wait) throws InterruptedException;
	
	/**
	 * Reset stuff before you try a reconnect. Usually this refers to await-timer but can be anything relevant to reconnection.
	 */
	public void reset();
	
	/**
	 * Add comment: @OnWebSocketClose to method!<br>
	 * Triggers when the webSocket server closes the connection.
	 * 
	 * @param statusCode - send by server
	 * @param reason - sent by server
	 */
	public void onClose(int statusCode, String reason);
	
	/**
	 * Add comment: @OnWebSocketConnect to method!<br>
	 * Triggers when the client is connected to server.
	 * 
	 * @param session - session sent by server
	 */
	public void onConnect(Session session);
	
	/**
	 * Add comment: @OnWebSocketMessage to method!<br>
	 * Triggers when the client receives a message.
	 * 
	 * @param msg - message sent by server
	 */
	public void onMessage(String msg);
	
	/**
	 * Add comment: @OnWebSocketError to method!<br>
	 * @param session - session sent by server
	 * @param error - throwable error
	 */
	public void onError(Session session, Throwable error);
	
	/**
	 * Send a message to the server.
	 * @param msg - SocketMessage
	 * @param wait - maximum wait time in ms
	 * @return did the server receive the message? true/false
	 */
	public boolean sendMessage(SocketMessage msg, long wait);
	
	/**
	 * Close connection to webSocket server.
	 */
	public void close();
	
	/**
	 * Get name that belongs to userId (set in account). Note: the variable "username" is actually userId and does not
	 * match with the return value of this method ... which is the "real" user name. 
	 * The user name is NOT unique! (userId is!).
	 */
	//public String getUserName();
	
	/**
	 * Get userId. Note: the variable "username" is actually userId and this is what this method returns. It is the only unique id besides the session itself
	 * and the "link" to the user account.
	 */
	public String getUserId();
	
	/**
	 * Get active session.
	 */
	public Session getSession();
	
	/**
	 * Do track user lists? By default this is disabled because if the client is an assistant he might be in a lot of channels at the same time.
	 * Activate it if the client only has a small amount of channels and users.
	 */
	public void setStoreChannelUserLists(boolean doIt);
	
	/**
	 * Get active users.
	 */
	public JSONArray getUserList(String channelId);
	
	/**
	 * Get active channel id.
	 */
	public String getActiveChannel();
}
