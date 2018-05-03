package net.b07z.sepia.websockets.client;

import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.tools.JSON;

/**
 * Example of a simple Echo Client.
 */
public class StartWebSocketClient
{
    public static void main(String[] args)
    {
    	JSONObject credentials = JSON.make("userId", "enterValidId", "pwd", "enterValidPassword");
        SocketClient mySocket = new SepiaSocketClient(credentials);
        SocketClientHandler mySocketHandler = new SocketClientHandler(mySocket);
        mySocketHandler.tryReconnect = true;
        mySocketHandler.connect();
        
        //as long as tryReconnect is true this line is never reached
    }
}
