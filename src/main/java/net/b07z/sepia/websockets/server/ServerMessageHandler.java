package net.b07z.sepia.websockets.server;

import org.eclipse.jetty.websocket.api.Session;

import net.b07z.sepia.websockets.common.SocketMessage;

/**
 * Interface to handle messages sent to server. Usually used inside {@link SepiaSocketHandler} 
 * and instantiated with reference to {@link SocketServer} for broadcasting and user handling.
 * 
 * @author Florian Quirin
 */
public interface ServerMessageHandler {
	
	/**
	 * Handle {@link SocketMessage}.
	 */
	public void handle(Session userSession, SocketMessage msg) throws Exception;

}
