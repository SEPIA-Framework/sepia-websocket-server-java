package net.b07z.sepia.websockets.client;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketMessage.DataType;
import net.b07z.sepia.websockets.common.SocketMessage.SenderType;

/**
 * Basic SEPIA webSocket client that connects to the server and listens to Messages.<br>
 * Can (should) be extended by overwriting the message methods.
 *  
 * @author Florian Quirin
 *
 */
@WebSocket
public class SepiaSocketClient implements SocketClient{
	//also possible @WebSocket(maxTextMessageSize = 64 * 1024)
	static Logger log = LoggerFactory.getLogger(SepiaSocketClient.class);
	
	//--- SESSION DATA ---
	private Session session;
	private String username;
	private String givenName;
	private String activeChannel;
	private JSONObject credentials;
	private JSONObject clientParameters; 		//<- things like client info, environment, etc. that defines the client
	//private JSONArray userList;
	private boolean storeChannelUserLists = false;
	private Map<String, JSONArray> channelUserLists = new ConcurrentHashMap<>();
	private long timeOfLastAction = 0;
	private AtomicInteger activeThreads = new AtomicInteger(0);
	private int maxThreadsRegistered = 0;
	//
	
    private CountDownLatch connectLatch;
    private CountDownLatch closeLatch;

    /**
     * Create the default SEPIA messages client.
     */
    public SepiaSocketClient()
    {
        connectLatch = new CountDownLatch(1);
        closeLatch = new CountDownLatch(1);
    }
    /**
     * Create the default SEPIA messages client with credentials to authenticate against server.
     * @param credentials - JSONObject with "userId" and "pwd" (parameters like client info will not be sent)
     */
    public SepiaSocketClient(JSONObject credentials)
    {
        if (credentials.get("pwd") != null && credentials.get("userId") != null){
        	this.credentials = credentials;
        	this.username = (String) credentials.get("userId");
        }
        
    	connectLatch = new CountDownLatch(1);
        closeLatch = new CountDownLatch(1);
    }
    /**
     * Create the default SEPIA messages client with credentials to authenticate against server.
     * Parameters are set as well here so you can give a specific client info, environment etc..
     * @param credentials - JSONObject with "userId" and "pwd" (parameters like client info will not be sent)
     * @param clientParameters - things that specify the client like info, environment, location, time etc.
     */
    public SepiaSocketClient(JSONObject credentials, JSONObject clientParameters)
    {
        if (credentials.get("pwd") != null && credentials.get("userId") != null){
        	this.credentials = credentials;
        	this.username = (String) credentials.get("userId");
        }
        this.clientParameters = clientParameters;
        
    	connectLatch = new CountDownLatch(1);
        closeLatch = new CountDownLatch(1);
    }
    
    /**
     * How many threads are active?
     */
    @Override
    public int getActiveThreads(){
    	return activeThreads.get();
    }
    /**
     * How many threads were active at max?
     */
    @Override
    public int getMaxRegisteredThreads(){
    	return maxThreadsRegistered;
    }
    /**
     * Get statistics about this socket client in readable form (HTML formatted).
     */
    @Override
    public String getStats(){
    	return("" +
    		"Active threads now: " + getActiveThreads() + "<br>" + 
    		"Max. active threads: " + getMaxRegisteredThreads() + "<br>"
    	);
    }
    
    //------ programmable message analysis ------
    
    /**
     * Triggered when this client gets a private message.
     */
    public void replyToMessage(SocketMessage msg){
    	System.out.printf("Got msg: '%s' and sent reply%n", msg.text);
		SocketMessage reply = new SocketMessage(activeChannel, username, msg.sender, "Interesting, tell me more!", "default");
		if (msg.msgId != null) reply.setMessageId(msg.msgId);
		sendMessage(reply, 3000);
    }
    /**
     * Triggered when this client reads an arbitrary chat message
     */
    public void commentChat(SocketMessage msg){
    	if (msg.text.toLowerCase().contains("wetter")){
			System.out.printf("Saw 'wetter' and said something%n");
			SocketMessage reply = new SocketMessage(activeChannel, username, "", "Wetter ist so la la oder?", "default");
			reply.setSenderType(SenderType.assistant.name());
			if (msg.msgId != null) reply.setMessageId(msg.msgId);
	        sendMessage(reply, 3000);
		}else{
			System.out.printf("Got msg: '%s'%n", msg.getJSON().toJSONString());
		}
    }
    /**
     * Triggered when the server sends a status message.
     */
    public void checkStatusMessage(SocketMessage msg){
    	System.out.printf("Got server msg: %s%n", msg.getJSON().toJSONString());
		
		//send alo when a user joined
		if (msg.text != null && msg.text.contains("joined")){
			System.out.printf("Saw 'join' and said alo%n");
			SocketMessage reply = new SocketMessage(activeChannel, username, "", "Alo, alo, Grahhaaaaaam!", "default");
			reply.setSenderType(SenderType.assistant.name());
			if (msg.msgId != null) reply.setMessageId(msg.msgId);
	        sendMessage(reply, 3000);
		}
    }
    /**
     * Triggered when the user successfully joined a new channel
     */
    public void joinedChannel(String activeChannel, String givenName){
    	System.out.println(username + " joined channel '" + activeChannel + "'");
    }
    /**
     * Triggered when the user or someone/something else joined the channel
     */
    public void welcomeToChannel(String channelId){
    	System.out.println("Channel '" + channelId + "' has " + getUserList(channelId) + " active users.");
    }
    
    //-------------------------------------------

    @Override
    public boolean awaitConnection(long wait) throws InterruptedException{
        return connectLatch.await(wait, TimeUnit.MILLISECONDS);
    }
    @Override
    public boolean awaitClose(long wait) throws InterruptedException{
        return closeLatch.await(wait, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void reset(){
    	connectLatch.countDown();
    	close();
    	connectLatch = new CountDownLatch(1);
        closeLatch = new CountDownLatch(1);
        username = null;
        givenName = null;
        activeChannel = null;
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason){
        System.out.printf("WEBSOCKET-CLIENT: Connection closed - last action: %d - reason: %d - %s%n", (System.currentTimeMillis()-timeOfLastAction), statusCode, reason);
        session = null;
        closeLatch.countDown();
        username = null;
        givenName = null;
        activeChannel = null;
    }

    @OnWebSocketConnect
    public void onConnect(Session session){
        log.info("WEBSOCKET-CLIENT: Got connection");
        timeOfLastAction = System.currentTimeMillis();
        //session.setIdleTimeout(0);
        //session.getPolicy().setIdleTimeout(SocketConfig.IDLE_TIMEOUT);
        this.session = session;
        connectLatch.countDown();
    }
    
	@OnWebSocketError
	public void onError(Session session, Throwable error) {
		log.error("WEBSOCKET-CLIENT: Socket ERROR: " + error.getMessage());
		Debugger.printStackTrace(error, 4);
		//error.printStackTrace();
	}

    @OnWebSocketMessage
    public void onMessage(String msg){
    	timeOfLastAction = System.currentTimeMillis();
    	Thread thread = new Thread(() -> {
    		int activeT = activeThreads.incrementAndGet();
    		if (activeT > maxThreadsRegistered) maxThreadsRegistered = activeT;
	    	try {
				SocketMessage message = SocketMessage.importJSON(msg);
				String msgId = message.msgId;
				String channelId = message.channelId;
				//System.out.println(message.getJSON()); 		//debug
				
				//update stuff from server
				if (message.sender.equalsIgnoreCase(SocketConfig.SERVERNAME)){
					if (storeChannelUserLists && message.userList != null && !message.userList.isEmpty() && channelId != null && !channelId.isEmpty()){
						channelUserLists.put(channelId, message.userList);
						log.info("WEBSOCKET-CLIENT: Updated userList of channel '" + channelId + "': " + message.userList.toString());
					}
				}
				
				//data (note: sending data to a specific receiver will also send it back to you, is that OK?) 
				if (message.data != null){
					String dataType = (String) message.data.get("dataType");
					//only credentials?
					if (dataType == null){
						//Note: handled in the final interface implementation (in the overwritten methods replyToMessage, ...)?
						//System.out.println("WEBSOCKET-CLIENT: Got data without comment: " + message.data.toJSONString());
					
					}else if (dataType.equals(DataType.directCmd.name()) || dataType.equals(DataType.assistAnswer.name())){
						//Note: handled in the final interface implementation (in the overwritten methods replyToMessage, ...)?
						
					//data: authentication
					}else if (dataType.equals(DataType.authenticate.name())){
						//set credentials
						JSONObject data = JSON.make("dataType", DataType.authenticate.name());
						if (credentials != null && !credentials.isEmpty()){
							username = (String) credentials.get("userId");
							JSON.add(data, "credentials", credentials);
						}
						//set parameters
						if (clientParameters != null && !clientParameters.isEmpty()){
							JSON.add(data, "parameters", clientParameters);
						}
						log.info("WEBSOCKET-CLIENT: Authenticating user: '" + username + "'"); 		//debug
						
						SocketMessage msgUserName = new SocketMessage("", username, SocketConfig.SERVERNAME, data);
						if (msgId != null) msgUserName.setMessageId(msgId);
						boolean msgSent = sendMessage(msgUserName, 3000);
						if (!msgSent){
							//TODO: now what?
						}
					
					}else if (dataType.equals(DataType.joinChannel.name())){
						activeChannel = (String) message.data.get("channelId");
						givenName = (String) message.data.get("givenName");
						joinedChannel(activeChannel, givenName);
					
					}else if (dataType.equals(DataType.welcome.name())){
						welcomeToChannel(channelId);
					}
				}
	
				//message not from myself and server - like a user chats with assistant
				if (!message.sender.equalsIgnoreCase(username) && !message.sender.equalsIgnoreCase(SocketConfig.SERVERNAME.toLowerCase())){
					//send reply to personal message
					if (message.receiver != null && message.receiver.toLowerCase().equals(username)){
						replyToMessage(message);
						
					//send comment to chat?
					}else{
						commentChat(message);
					}
	
				//message from server - like server status message
				}else{
					checkStatusMessage(message);
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
	    	activeThreads.decrementAndGet();
    	});
    	thread.start();
    }
    
    @Override
    public boolean sendMessage(SocketMessage msg, long wait){
    	try{
    		if (session == null || !session.isOpen()){
    			System.err.println(this.getClass().getName() + " - WEBSOCKET-CLIENT: " + "session is not open or null.");
    			return false;
    		}
            Future<Void> fut;
            fut = session.getRemote().sendStringByFuture(msg.getJSON().toJSONString());
            fut.get(wait, TimeUnit.MILLISECONDS); 
            return true;
            
        }catch (Throwable t){
            t.printStackTrace();
            return false;
        }
    }
    
    @Override
    public void close(){
    	if (session != null){
    		session.close(StatusCode.NORMAL, "I'm done");
    		session = null;
    	}
    	closeLatch.countDown();
    }
    
    @Override
    public String getUserId(){
    	return username;
    }
    
    @Override
    public Session getSession(){
    	return session;
    }
    
    @Override
    public JSONArray getUserList(String channelId){
    	JSONArray userList = channelUserLists.get(channelId);
    	if (userList == null){
    		return new JSONArray();
    	}else{
    		return userList;
    	}
    }
    @Override
	public void setStoreChannelUserLists(boolean doIt) {
		storeChannelUserLists = doIt;
	}
    
    @Override
    public String getActiveChannel(){
    	return activeChannel;
    }
}