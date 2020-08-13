package net.b07z.sepia.websockets.server;

import net.b07z.sepia.server.core.server.BasicStatistics;
import net.b07z.sepia.server.core.tools.ThreadManager;
import net.b07z.sepia.websockets.common.SocketUserPool;

/**
 * Track all sorts of statistics like total hits, time authorization took, API calls, etc.
 * 
 * @author Florian Quirin
 *
 */
public class Statistics extends BasicStatistics {
	
	public static String getInfo(){
		String msg = 
			"Active clients: " + SocketUserPool.getAllUsers().size() + "<br>" +
			"Pending sessions: " + SocketUserPool.getAllPendingSessions().size() + "<br>" +
			"<br>" +
			"Processing threads:<br>" +
			"Active threads now: " + ThreadManager.getNumberOfCurrentlyActiveThreads() + "<br>" +
			"Max. active threads: " + ThreadManager.getMaxNumberOfActiveThreads() + "<br>" +
			"Scheduled alive-pings: " + SepiaClientPingHandler.getNumberOfScheduledPingRequest() + "<br>" +
			"<br>"
		;
		//add basics
		msg += getBasicInfo(); 		//TODO: fill data for endpoints
		
		return msg;
	}
}
