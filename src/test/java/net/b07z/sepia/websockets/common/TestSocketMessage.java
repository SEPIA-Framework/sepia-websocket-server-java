package net.b07z.sepia.websockets.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.b07z.sepia.server.core.data.Role;

public class TestSocketMessage {
	
	//TODO: rewrite to JUnit test

	public static void main(String[] args) {
		
		Map<String, SocketUser> userIdMap = new ConcurrentHashMap<>();
		userIdMap.put("aId", new SocketUser(null, "aId", "aName", Role.user));
		userIdMap.put("bId", new SocketUser(null, "bId", "bName", Role.user));
		userIdMap.put("cId", new SocketUser(null, "cId", "cName", Role.assistant));
		
		SocketMessage msg = new SocketMessage("channel1", SocketConfig.SERVERNAME, "aId", "test message", "");
    	msg.html = BuildHTML.getMessageFromSender(SocketConfig.SERVERNAME, "test message");
    	msg.setUserList(userIdMap.values());
    	
    	String msgString = msg.getJSON().toJSONString();
    	System.out.println(msgString);
    	
    	try {
			SocketMessage msg2 = SocketMessage.importJSON(msgString);
			System.out.println(msg2.userList);
			
			System.out.println("html identical? " + msg.html.equals(msg2.html));
			System.out.println("userList identical? " + msg.userList.containsAll(msg2.userList));
			
		} catch (Exception e) {
			e.printStackTrace();
		} 

	}

}
