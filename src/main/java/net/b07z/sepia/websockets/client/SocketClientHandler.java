package net.b07z.sepia.websockets.client;
import java.net.URI;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;

/**
 * Handles a SocketClient by taking care of connecting and connection loss etc.
 */
public class SocketClientHandler
{
	WebSocketClient client;
	SocketClient socket;
	String destURI;
	
	boolean isConnecting = false;
	boolean waitingToReconnect = false;
	boolean connectionIsOpen = false;
	
	boolean tryReconnect = true;
	private boolean abortReconnect = false;
	int connectAttempts = 0;
	private long nextReconnectBasis = 500;
	private long reconnectMaxWait = 30000;
	long nextReconnectTry = 0;
	long waitingToReconnectSince = 0;
	
	private static final Logger log = LoggerFactory.getLogger(SocketClientHandler.class);
	
	/**
	 * Create new client that can connect to a WebSocket server.
	 * @param socket - client used to connect
	 */
	public SocketClientHandler(SocketClient socket){
		if (SocketConfig.isSSL){													
			SslContextFactory sslContextFactory = new SslContextFactory();
		    sslContextFactory.setKeyStorePath("Xtensions/SSL/ssl-keystore.jks");
		    sslContextFactory.setKeyStorePassword(SocketConfig.keystorePwd); 		
		    client = new WebSocketClient(sslContextFactory);
		}else{
			client = new WebSocketClient();
		}
		client.getPolicy().setIdleTimeout(SocketConfig.IDLE_TIMEOUT);
		client.setMaxIdleTimeout(SocketConfig.IDLE_TIMEOUT);
		client.setAsyncWriteTimeout(SocketConfig.ASYNC_TIMEOUT);
		
		this.socket = socket;
	}
	
	/**
     * Connect client to localHost server.
     */
    public void connect(){
    	if (SocketConfig.isSSL){
    		connect(SocketConfig.webSocketSslEP);
    	}else{
    		connect(SocketConfig.webSocketEP);
    	}
    }
    /**
     * Connect client to server with URI.
     * @param destUri - URI of the server
     */
	public void connect(String destURI)
    {
		this.destURI = destURI;
		if (isConnecting || waitingToReconnect){
			log.error("WEBSOCKET-CLIENT - Already (re)connecting!");
			return;
		}
        try{
            client.start();

            URI serverURI = new URI(destURI);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            client.connect(socket, serverURI, request);
            isConnecting = true;
            connectAttempts++;
            log.info("WEBSOCKET-CLIENT - Connecting to: " + serverURI);

            // wait X ms for socket connection.
            connectionIsOpen = socket.awaitConnection(3000);
            isConnecting = false;
            
            if (connectionIsOpen){
            	connectAttempts = 0;
            	socket.awaitClose(Long.MAX_VALUE);
            	
            }else{
            	log.error("WEBSOCKET-CLIENT - Connection failed!");
            }

            connectionIsOpen = false;
        
        }catch (InterruptedException e){
        	e.printStackTrace();
        
		}catch (Exception e) {
			e.printStackTrace();
			
		}finally{
			connectionIsOpen = false;
			isConnecting = false;
            try{
                client.stop();
            }catch (Exception e){
                e.printStackTrace();
            }
            
            //reconnect?
            if (tryReconnect){
            	reconnect();
            }
        }
    }
	
	/**
	 * Try to reconnect with exponentially increasing wait time.
	 */
	public void reconnect(){
		socket.reset();
		try {
			waitingToReconnect = true;
			abortReconnect = false;
			long waitMax = Math.min(reconnectMaxWait, (connectAttempts * connectAttempts * nextReconnectBasis));
			nextReconnectTry = System.currentTimeMillis() + waitMax;
			waitingToReconnectSince = System.currentTimeMillis();
			long waitAlready = 0;
			while (!abortReconnect && (waitAlready <= waitMax)){
				Thread.sleep(500);
				waitAlready += 500;
			}
			waitingToReconnect = false;
			
			//TODO: ping server first to see if connect has a chance 
			
			connect(destURI);
		
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Abort reconnection process. Can take up to 500ms.
	 */
	public boolean abortReconnect(){
		tryReconnect = false;
		abortReconnect = true;
		try{
			long waitMax = 600;
			long waitAlready = 0;
			while (waitingToReconnect && (waitAlready <= waitMax)){
				Thread.sleep(100);
				waitAlready += 105;
			}
			socket.close();
			return (!waitingToReconnect);
			
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Try to reconnect after connection loss?
	 */
	public void setTryReconnect(boolean tryReconnect){
		this.tryReconnect = tryReconnect;
	}
	
	/**
	 * Send message over webSocket connection.
	 * @param msg - SocketMessage, adds sender automatically.
	 * @param wait - max. wait time
	 * @return sent successfully?
	 */
	public boolean send(SocketMessage msg, long wait){
		msg.sender = socket.getUserId();
		return socket.sendMessage(msg, wait);
	}
	
	/**
	 * Close webSocket connection.
	 */
	public void close(){
		if (tryReconnect){
			abortReconnect();
		}else{
			socket.close();
		}
	}
	
	/**
	 * Is the webSocket client currently trying to connect or reconnect?
	 */
	public boolean isConnecting(){
		return (isConnecting || waitingToReconnect);
	}
	
	/**
	 * Is the connection open?
	 */
	public boolean isOpen(){
		return connectionIsOpen;
	}
}
