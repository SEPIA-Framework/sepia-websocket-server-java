package net.b07z.sepia.websockets.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.ThreadManager;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;
import net.b07z.sepia.websockets.common.SocketUser;
import net.b07z.sepia.websockets.common.SocketUserPool;

/**
 * Handle ping requests (send, observe, remove, etc.).
 * 
 * @author Florian Quirin
 */
public class SepiaClientPingHandler implements ServerMessageHandler {
	
	static Logger log = LoggerFactory.getLogger(SepiaClientPingHandler.class);
	
	public static final long PING_EXPIRE_TIME_MS = 10000;
	public static final long PING_NEXT_DELAY_MS = 1000*60*45;
	
	//ping request tracking
	private static Map<String, PingRequest> openPingRequests = new ConcurrentHashMap<>();
	public static int getNumberOfScheduledPingRequest(){
		return openPingRequests.size();
	}
	
	SocketServer server;
	
	/**
	 * Class representing a client ping request.
	 */
	public static class PingRequest {
		public final String pingId;
		private Session userSession;
		private long expireTs = 0;
		
		private SocketMessage pingMsg;
		private BooleanSupplier cancelRequestFun;
		private BooleanSupplier cancelResultObserverFun;
		
		private boolean resolvedInTime = false;
		
		/**
		 * New ping request.
		 * @param userSession - User session that was "pinged"
		 */
		public PingRequest(Session userSession) {
			this.userSession = userSession;
			this.pingMsg = SepiaSocketBroadcaster.makeServerClientPingMessage();
			this.pingId = this.pingMsg.msgId;
			
			openPingRequests.put(pingId, this);
		}
		
		public void activate(long delay){
			if (delay <= 0) delay = PING_NEXT_DELAY_MS + Math.round((Math.random() * 10000)); //default + random offset
			if (cancelRequestFun == null){
				scheduleRequest(delay);
			}
		}
		
		private void scheduleRequest(long delay){
			//use thread manager
			cancelRequestFun = ThreadManager.scheduleBackgroundTaskAndForget(delay, () -> {
				if (userSession != null && userSession.isOpen()){
					//send via session and observer result until expired
					SepiaSocketBroadcaster.broadcastMessageToSession(this.pingMsg, this.userSession);
					scheduleObserveResult();
				}else{
					//this should theoretically never happen (because 'cancelRequestFun' should be called to prevent it), but ...
					openPingRequests.remove(this.pingId);
				}
			});
		}
		
		private void scheduleObserveResult(){
			this.expireTs = System.currentTimeMillis() + PING_EXPIRE_TIME_MS;
			//use thread manager
			cancelResultObserverFun = ThreadManager.scheduleBackgroundTaskAndForget(PING_EXPIRE_TIME_MS, () -> {
				//check result after PING_EXPIRE_TIME_MS
				resolveRequest();
			});
		}
		
		public boolean isExpired(){
			return (this.expireTs > 0 && System.currentTimeMillis() > this.expireTs);
		}
		
		public boolean resolveRequest(){
			//close open observer thread
			if (cancelResultObserverFun != null){
				cancelResultObserverFun.getAsBoolean();
			}
			if (!isExpired()){
				this.resolvedInTime = true;
			}else{
				this.resolvedInTime = false;
			}
			openPingRequests.remove(pingId);
			SocketUser user = (userSession != null)? SocketUserPool.getUserBySession(userSession) : null;
			if (user != null){
				user.setInfo("nextPingRequest", null);
			}
			if (!this.resolvedInTime){
				//Client did not answer in time ...
				if (userSession != null && userSession.isOpen()){
					log.error("Client did not answer in time after alive-ping. Resetting connection for: " 
							+ (user != null? user.getUserId() : "unknown"));
					userSession.close();
				}else if (userSession != null){
					if (user != null){
						log.error("Client did not answer in time after alive-ping and connection seems lost. Resetting data for: " 
								+ (user != null? user.getUserId() : "unknown"));
						SocketUserPool.removeUser(user);
						//TODO: is this enough?
					}
					SocketUserPool.removePendingSession(userSession);	//just to make sure
				}
			}
			return this.resolvedInTime;
		}
		
		public boolean cancelScheduledPing(){
			if (cancelRequestFun != null){
				openPingRequests.remove(pingId);
				return cancelRequestFun.getAsBoolean();
			}else{
				return false;
			}
		}
		
		//TODO: what about a "cancelResultObserver" method?
	}
	
	/**
	 * Create new handler for SEPIA messages.
	 */
	public SepiaClientPingHandler(SocketServer server){
		this.server = server;
	}
	
	/**
	 * Schedule a ping to see if user is still online and return the ping ID (or null).
	 * @param userSession - session of user
	 * @param overwriteDelay - custom delay or 0 (or -1) for default
	 * @return ping ID or null
	 */
	public static String scheduleNextUserPing(Session userSession, long overwriteDelay){
		SocketUser user = SocketUserPool.getUserBySession(userSession);
		if (userSession != null && userSession.isOpen()){
			//create request
			PingRequest pr = new PingRequest(userSession);
			if (overwriteDelay > 0){
				pr.activate(overwriteDelay);
			}else{
				pr.activate(-1);
			}
			if (user != null){
				//TODO: clean-up here before setting new ?
				user.setInfo("nextPingRequest", pr);
				//System.out.println("PING - scheduleNextUserPing: " + user.getUserId());				//DEBUG
			}
			return pr.pingId;
		
		}else{
			//make sure there is nothing left
			if (userSession != null) SocketUserPool.removePendingSession(userSession);
			if (user != null) SocketUserPool.removeUser(user);
			return null;
		}
	}

	@Override
	public void handle(Session userSession, SocketMessage msg) throws Exception {
		if (msg.data == null) return;
		
		//Get ping reply ID 
		String replyId = JSON.getString(msg.data, "replyId");
	
		//Check ID
		if (Is.notNullOrEmpty(replyId) && openPingRequests.containsKey(replyId)){
			PingRequest pr = openPingRequests.get(replyId);
			if (pr != null){
				boolean success = pr.resolveRequest();
				if (success){
					//schedule next call
					if (SocketConfig.useAlivePings){
						scheduleNextUserPing(userSession, -1);
					}
				}
			}
			
		//Request ping?
		}else{
			long sendPing = JSON.getLongOrDefault(msg.data, "sendPing", 0);
			if (sendPing > 0){
				//send ping now
				String pingId = scheduleNextUserPing(userSession, sendPing);
				//... but only once
				if (Is.notNullOrEmpty(pingId)) openPingRequests.remove(pingId);
				//TODO: if the regular procedure runs it will probably break here
			
			}else if (sendPing == -1){
				//send ping now and continue
				scheduleNextUserPing(userSession, sendPing);
			
			}else{
				//Ignore for now!?
			}
		}
	}

}
