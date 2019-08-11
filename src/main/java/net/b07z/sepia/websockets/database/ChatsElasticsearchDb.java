package net.b07z.sepia.websockets.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.websockets.common.SocketConfig;

public class ChatsElasticsearchDb implements ChatsDatabase {

	private static final Logger log = LoggerFactory.getLogger(ChannelsElasticsearchDb.class);
	private static final String ES_CHATS_PATH = SocketConfig.DB_CHAT + "/" + "channel_data";

	String esServerUrl = "";
	
	public ChatsElasticsearchDb() throws Exception{
		esServerUrl = ConfigElasticSearch.getEndpoint(SocketConfig.defaultRegion);
		//TODO: implement
		throw new Exception("This database module is not yet supported!");
	}

	public ChatsElasticsearchDb(String serverUrl){
		esServerUrl = serverUrl;
	}
}
