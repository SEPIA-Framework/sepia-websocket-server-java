package net.b07z.sepia.websockets.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketChannelPool;
import net.b07z.sepia.websockets.common.SocketConfig;

public class Test_ChannelsDatabase {
	
	private static ChannelsDatabase getInMemoryDb(){
		return new ChannelsInMemoryDb();
	}
	private static ChannelsDatabase getElasticsearchDb() {
		return new ChannelsElasticsearchDb();
	}

	public static void main(String[] args) throws Exception {
		
		SocketConfig.loadSettings("Xtensions/websocket.test.properties");
		//TODO: we might need to add more init methods from start here
		
		boolean useElasticsearch = true; //(args != null && args.length > 0 && args[0].equalsIgnoreCase("elasticsearch"));
		
		ChannelsDatabase chanDb = (useElasticsearch)? getElasticsearchDb() : getInMemoryDb();
		
		//clean-up first
		long removed = 0;
		for (String s : Arrays.asList("uid105", "uid107", "uid109", "uid111")){
			long r = chanDb.removeAllChannelsOfOwner(s);
			if (r == -1){
				throw new Exception("Failed to delete old entries for userId: " + s);
			}else {
				removed += r;
			}
		}
		System.out.println("Old entries removed: " + removed);
		//Give database time to refresh
		System.out.println("Wait for DB refresh...");
		Debugger.sleep(2000);
		
		List<String> chanNames = Arrays.asList(
				"Channel A",
				"Channel B",
				"Channel C",
				"Channel D",
				"Channel E"
		);
		String userId1 = "uid107";
		String userId2 = "uid109";
		String userId3 = "uid111";
		List<String> chanOwners = Arrays.asList(
				userId1,
				userId2,
				userId3,
				userId1,
				userId1
		);
		
		//Create
		List<SocketChannel> channels = new ArrayList<>();
		for (int i=0; i<chanNames.size(); i++) {
			String chanId = SocketChannelPool.getRandomUniqueChannelId();
			Set<String> members = new HashSet<>();
			members.add("uid105");
			boolean addAssistant = false;
			SocketChannel sc = SocketChannelPool.createChannel(chanId, chanOwners.get(i), false, chanNames.get(i), members, addAssistant);
			if (chanDb.storeChannel(sc) != 0){
				throw new Exception("Failed to store channel!");
			}
			channels.add(sc);
		}
		//Give database time to refresh
		System.out.println("Wait for DB refresh...");
		Debugger.sleep(2000);
		
		//Check number of channels
		int numChannelsOfUser1 = chanDb.getAllChannelsOwnedBy(userId1).size();
		int numChannelsOfUser2 = chanDb.getAllChannelsOwnedBy(userId2).size();
		
		if (numChannelsOfUser1 != 3 || numChannelsOfUser2 != 1){
			throw new Exception("Wrong number of channels found!");
		}else {
			System.out.println("User 1 has this many channels: " + numChannelsOfUser1);
			System.out.println("User 2 has this many channels: " + numChannelsOfUser2);
		}
		
		//Get a channel numbers
		SocketChannel sc3 = chanDb.getChannelWithId(channels.get(2).getChannelId());
		if (!sc3.getOwner().equalsIgnoreCase(userId3)){
			throw new Exception("Channel 3 has wrong owner!");
		}else{
			System.out.println("Channel 3 has owner: " + sc3.getOwner());
		}
		
		//Delete channel
		if (chanDb.removeChannel(channels.get(1).getChannelId()) != 0){
			throw new Exception("Failed to remove channel!");
		}
		//Give database time to refresh
		System.out.println("Wait for DB refresh...");
		Debugger.sleep(2000);
		
		numChannelsOfUser2 = chanDb.getAllChannelsOwnedBy(userId2).size();
		if (numChannelsOfUser2 != 0){
			throw new Exception("Wrong number of channels found for user 2 after removal of his only channel!");
		}else {
			System.out.println("User 2 has this many channels after removal process: " + numChannelsOfUser2);
		}
		
		//Get channel data
		SocketChannel sc1 = chanDb.getChannelWithId(channels.get(0).getChannelId());
		System.out.println("Data of first channel: " + sc1.getJson());
		
		System.out.println("DONE");
	}

}
