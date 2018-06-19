package net.b07z.sepia.websockets.server;
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
/**
 * Abstract handler that implements the webSocket interface and redirects events to the given "real" server handler.<br>
 * To use it set static "SocketServer server" for this class and implement your server there.  
 * 
 * @author Florian Quirin
 *
 */
@WebSocket
public class AbstractSocketHandler {
	
	public static SocketServer server; 
	
    @OnWebSocketConnect
    public void onConnect(Session userSession) throws Exception {
    	server.onConnect(userSession);
    }

    @OnWebSocketClose
    public void onClose(Session userSession, int statusCode, String reason) {
        server.onClose(userSession, statusCode, reason);
    }

    @OnWebSocketMessage
    public void onMessage(Session userSession, String message) {
    	server.onMessage(userSession, message);
    }
}
