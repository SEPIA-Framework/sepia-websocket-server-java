package net.b07z.sepia.websockets.server;

import java.util.Collection;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;

import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketUser;

public interface SocketServer {
	
	//--- webSocket default interface ---
	
	public void onConnect(Session userSession) throws Exception;
	
	public void onClose(Session userSession, int statusCode, String reason);
	
	public void onMessage(Session userSession, String message);
	
	//--- broadcasting ---
	
	public long getLastBroadcastTime();
	
	/**
	 * This method automatically selects the users that shall receive the message (collection, channel, session?).
	 */
	public void broadcastMessage(SocketMessage msg);
	
	public void broadcastMessage(SocketMessage msg, String channelId);
	
	public void broadcastMessage(SocketMessage msg, Session session);
	
	public void broadcastMessage(JSONObject msg, Session session);
	
	//--- user handling ---
    
    /**
     * Make a unique user name for connecting guests (that can become authenticated users in the next step). If the guest does not authenticate
     * this will be his receiver id.
     */
	public String makeUserName();
    
    public void storeUser(SocketUser socketUser);
    
    public void removeUser(SocketUser socketUser);
    
    public Collection<SocketUser> getAllUsers();
    
    public SocketUser getUserBySession(Session session);

}
