package net.b07z.sepia.websockets.database;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;

public class Test_ChatsDatabase {
	
	private static ChatsDatabase getInMemoryDb(){
		return new ChatsInMemoryDb();
	}
	private static ChatsDatabase getElasticsearchDb() {
		return new ChatsElasticsearchDb();
	}

	public static void main(String[] args) throws Exception {
		
		SocketConfig.loadSettings("Xtensions/websocket.test.properties");
		//TODO: we might need to add more init methods from start here
		
		boolean useElasticsearch = true; //(args != null && args.length > 0 && args[0].equalsIgnoreCase("elasticsearch"));
		
		//NOTE: InMemoryDb is a dummy and will not return any data at the moment
		ChatsDatabase chatDb = (useElasticsearch)? getElasticsearchDb() : getInMemoryDb();
		
		//clean-up first
		long removed = 0;
		for (String s : Arrays.asList("channelId1")){	//, "channelId2"
			int r = chatDb.removeOldChannelMessages(s, System.currentTimeMillis());
			if (r == -1){
				throw new Exception("Failed to delete old entries for userId: " + s);
			}else {
				removed += r;
			}
		}
		System.out.println("Old entries removed: " + removed);
		if (removed > 0){
			System.out.println("Restart for full run plz.");
			return;
		}
		//Give database time to refresh
		System.out.println("Wait for DB refresh...");
		Debugger.sleep(2000);

		List<SocketMessage> messages = Arrays.asList(
				new SocketMessage("channelId1", "uid107", "a1", "uid109", "b1", "Hallo uid109", null),
				new SocketMessage("channelId1", "uid107", "a1", "uid109", "b1", "Wie geht es dir", null),
				new SocketMessage("channelId1", "uid109", "b1", "uid107", "a1", "Hallo uid107", null),
				new SocketMessage("channelId1", "uid109", "b1", "uid107", "a1", "Mir geht es gut", null),
				
				new SocketMessage("channelId2", "uid111", "b2", "", "", "An older message from: " + System.currentTimeMillis(), null)
		);
		
		//Store
		for (SocketMessage msg : messages) {
			//SocketChannelHistory.addMessageToChannelHistory(msg.channelId, msg);
			int code = chatDb.storeChannelMessage(msg.getJSON());
			if (code != 0){
				throw new RuntimeException("Failed to store message");
			}else{
				System.out.println("Message stored");
			}
		}
		//Give database time to refresh
		System.out.println("Wait for DB refresh...");
		Debugger.sleep(2000);
		
		//Show stored messages
		long notOlderThan = 1567548568801l;
		List<SocketMessage> restoredMessagesCh1 = chatDb.getAllMessagesOfChannel("channelId1", 0);
		List<SocketMessage> restoredMessagesCh2 = chatDb.getAllMessagesOfChannel("channelId2", notOlderThan);
		List<SocketMessage> restoredMessagesCh2b = chatDb.getAllMessagesOfChannel("channelId2", 0);
		Collections.sort(restoredMessagesCh1, new SocketMessage.SortByTimestamp());
		Collections.sort(restoredMessagesCh2, new SocketMessage.SortByTimestamp());
		Collections.sort(restoredMessagesCh2b, new SocketMessage.SortByTimestamp());
		System.out.println("New messages: ");
		for (SocketMessage msg : restoredMessagesCh1){
			System.out.println(msg.toString() + " - " + msg.text + " - " + msg.timeStampUNIX);
		}
		System.out.println("Old messages - " + restoredMessagesCh2.size() + " of " + restoredMessagesCh2b.size() + ": ");
		for (SocketMessage msg : restoredMessagesCh2){
			System.out.println(msg.toString() + " - " + msg.text + " - " + msg.timeStampUNIX);
		}
		System.out.println("All older messages: ");
		for (SocketMessage msg : restoredMessagesCh2b){
			System.out.println(msg.toString() + " - " + msg.text + " - " + msg.timeStampUNIX);
		}
				
		System.out.println("DONE");
	}

}
