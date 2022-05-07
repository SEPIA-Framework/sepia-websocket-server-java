package net.b07z.sepia.websockets.common;

import java.util.Properties;

import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.tools.ClassBuilder;
import net.b07z.sepia.server.core.tools.FilesAndStreams;
import net.b07z.sepia.websockets.database.ChannelsDatabase;
import net.b07z.sepia.websockets.database.ChannelsElasticsearchDb;
import net.b07z.sepia.websockets.database.ChannelsInMemoryDb;
import net.b07z.sepia.websockets.database.ChatsDatabase;
import net.b07z.sepia.websockets.database.ChatsElasticsearchDb;
import net.b07z.sepia.websockets.database.ChatsInMemoryDb;
import net.b07z.sepia.websockets.database.ConfigElasticSearch;

/**
 * Some settings shared by server and client. 
 * 
 * @author Florian Quirin
 *
 */
public class SocketConfig {
	
	//Server
	public static int PORT = 20723;
	public static final String SERVERNAME = "SEPIA-Websocket-Server"; 	//this is to identify the server (in client as well)
	public static String localName = "sepia-websocket-server-1";		//**user defined local server name (works as server device ID as well)
	public static String localSecret = "123456";						//**user defined secret to validate local server
	public static final String apiVersion = "v1.3.4";
	public static String privacyPolicyLink = "";						//Link to privacy policy
	
	public static final boolean allowCORS = true;
	public static final int IDLE_TIMEOUT = 1000*60*60*4;
	public static final int ASYNC_TIMEOUT = 15000; 			//TODO: is this relevant? Can we set it for server too?
		
	//Shared key for internal communication
	public static String clusterKey = "KantbyW3YLh8jTQPs5uzt2SzbmXZyphW"; 	//**one step of inter-API communication security
	public static boolean allowInternalCalls = true;						//**allow calls between servers with shared secret auth.
		
	//Assistant(s) - removed for now, use ConfigDefaults.defaultAssistantUserId
	//public static String systemAssistantId = "";
	
	//Default modules (implementations of certain interfaces)
	public static String assistAPI = "http://localhost:20721/";			//link to Assist-API e.g. for authentication
	public static String teachAPI = "http://localhost:20722/";			//link to Teach-API
	public static String webSocketEP = "ws://localhost:" + PORT + "/messages/";			//link to webSocket server
	public static String webSocketSslEP = "wss://localhost:" + PORT + "/messages/";		//link to secure webSocket server
	public static String webSocketAPI = "http://localhost:" + PORT + "/";				//link to webSocket API (control for server)
	public static boolean isSSL = false;
	public static String keystorePwd = "13371337";
	public static final String authenticationModule = ConfigDefaults.defaultAuthModule;
	public static String chatsDbModule = ChatsInMemoryDb.class.getCanonicalName();
	public static String channelsDbModule = ChannelsInMemoryDb.class.getCanonicalName();
	
	//General server features
	public static boolean useAlivePings = true;			//ping all clients from time to time to make sure they are alive
	
	//General chat settings
	public static boolean distinguishUsersByDeviceId = true;		//allow 2 users with same ID to be active when device ID is different?
	public static boolean inUserChannelBroadcastOnlyToAssistantAndSelf = true;	//in user private channel don't broadcast to other devices
	public static int storeMessagesPerChannel = 0;		//messages to store per channel so offline users get to read them when they (re)join the channel
	public static int maxChannelsPerUser = 10;			//how many channels can a user (non-admin) own?
	public static int maxChannelsPerServer = 5000; 		//NOTE: this is limited by 'index.max_result_window' (10000) for Elasticsearch. If you increase this you need to adjust the ES methods!
	public static long channelCleanUpScheduleDelay = 1800000; 		//wait at least this long until automatic channel clean-up triggers 
	
	//----------database---------
	
	//Region settings
	public static final String REGION_US = "us";					//Region tag for US
	public static final String REGION_EU = "eu";					//Region tag for EU
	public static final String REGION_TEST = "custom";				//Region tag for TEST
	public static final String REGION_CUSTOM = "custom";			//Region tag for CUSTOM server
	public static String defaultRegion = REGION_CUSTOM;				//**Region for different cloud services (e.g. AWS or local)
		
	//DB structure/indices
	public static final String DB_CHAT_CHANNELS = "chat";			//chat channels - NOTE: the name originates from the old index structure
	public static final String DB_CHAT_MESSAGES = "chat-messages";	//chat messages
	public static final String DB_CHAT_USERS = "chat-users";		//chat users
	
	//Getters
	public static ChannelsDatabase getDefaultChannelsDatabase(){
		return (ChannelsDatabase) ClassBuilder.construct(channelsDbModule);
	}
	public static ChatsDatabase getDefaultChatsDatabase(){
		return (ChatsDatabase) ClassBuilder.construct(chatsDbModule);
	}
	
	//----------helpers----------
	
	/**
	 * Load server settings from properties file. 
	 */
	public static void loadSettings(String configFile){
		if (configFile == null || configFile.isEmpty()){
			LoggerFactory.getLogger(SocketConfig.class).error("loading settings from " + configFile + "... failed!");
			return;
		}
		try{
			Properties settings = FilesAndStreams.loadSettings(configFile);
			//server
			PORT = Integer.valueOf(settings.getProperty("server_port"));
			assistAPI = settings.getProperty("server_assist_api_url");
			teachAPI = settings.getProperty("server_teach_api_url");
			webSocketAPI = settings.getProperty("websocket_api_url");
			webSocketEP = settings.getProperty("websocket_endpoint_url");
			webSocketSslEP = settings.getProperty("websocket_ssl_endpoint_url");
			localName = settings.getProperty("server_local_name");
			localSecret = settings.getProperty("server_local_secret");
			clusterKey = settings.getProperty("cluster_key");
			allowInternalCalls = Boolean.valueOf(settings.getProperty("allow_internal_calls"));
			//policies
			privacyPolicyLink =  settings.getProperty("privacy_policy");
			//modules
			String channelsDbModuleType = settings.getProperty("module_channels_db", "in_memory");
			if (channelsDbModuleType.equals("in_memory")){
				channelsDbModule = ChannelsInMemoryDb.class.getCanonicalName();
			}else if (channelsDbModuleType.equals("elasticsearch")){
				channelsDbModule = ChannelsElasticsearchDb.class.getCanonicalName();
			}
			String chatsDbModuleType = settings.getProperty("module_chats_db", "in_memory");
			if (chatsDbModuleType.equals("in_memory")){
				chatsDbModule = ChatsInMemoryDb.class.getCanonicalName();
			}else if (chatsDbModuleType.equals("elasticsearch")){
				chatsDbModule = ChatsElasticsearchDb.class.getCanonicalName();
			}
			//assistant
			//systemAssistantId = settings.getProperty("systemAssistantId");
			//databases
			defaultRegion = settings.getProperty("db_default_region", "custom");
			ConfigElasticSearch.endpoint_custom = settings.getProperty("db_elastic_endpoint_custom", "http://localhost:20724");
			ConfigElasticSearch.endpoint_eu1 = settings.getProperty("db_elastic_endpoint_eu1");
			ConfigElasticSearch.endpoint_us1 = settings.getProperty("db_elastic_endpoint_us1");
			ConfigElasticSearch.auth_type = settings.getProperty("db_elastic_auth_type", null);
			ConfigElasticSearch.auth_data = settings.getProperty("db_elastic_auth_data", null);
			//general features
			useAlivePings = Boolean.parseBoolean(settings.getProperty("use_alive_pings", "true"));
			//chat
			maxChannelsPerUser = Integer.parseInt(settings.getProperty("max_channels_per_user", "10"));
			storeMessagesPerChannel = Integer.parseInt(settings.getProperty("store_messages_per_channel", "0"));
			channelCleanUpScheduleDelay = Long.parseLong(settings.getProperty("channel_clean_up_schedule_delay", "1800000"));	//default 30min
			
			LoggerFactory.getLogger(SocketConfig.class).info("loading settings from " + configFile + "... done.");
		}catch (Exception e){
			LoggerFactory.getLogger(SocketConfig.class).error("loading settings from " + configFile + "... failed!");
		}
	}
	/**
	 * Save server settings to file;
	 */
	public static void saveSettings(String configFile){
		if (configFile == null || configFile.isEmpty()){
			LoggerFactory.getLogger(SocketConfig.class).error("saving settings to " + configFile + "... failed!");
			return;
		}
		//save all personal parameters
		Properties config = new Properties();
		try{
			//server
			config.setProperty("server_port", String.valueOf(PORT));
			config.setProperty("server_assist_api_url", assistAPI);
			config.setProperty("server_teach_api_url", teachAPI);
			config.setProperty("websocket_api_url", webSocketAPI);
			config.setProperty("websocket_endpoint_url", webSocketEP);
			config.setProperty("websocket_ssl_endpoint_url", webSocketSslEP);
			config.setProperty("server_local_name", localName);
			config.setProperty("server_local_secret", localSecret);
			config.setProperty("cluster_key", "");
			config.setProperty("allow_internal_calls", String.valueOf(allowInternalCalls));
			//assistant
			//config.setProperty("systemAssistantId", systemAssistantId);
			//databases
			config.setProperty("db_default_region", defaultRegion);
			config.setProperty("db_elastic_endpoint_custom", ConfigElasticSearch.endpoint_custom);
			config.setProperty("db_elastic_endpoint_eu1", ConfigElasticSearch.endpoint_eu1);
			config.setProperty("db_elastic_endpoint_us1", ConfigElasticSearch.endpoint_us1);
			//general features
			config.setProperty("use_alive_pings", String.valueOf(useAlivePings));
			//chat
			config.setProperty("max_channels_per_user", String.valueOf(maxChannelsPerUser));
			config.setProperty("store_messages_per_channel", String.valueOf(storeMessagesPerChannel));
			config.setProperty("channel_clean_up_schedule_delay", String.valueOf(channelCleanUpScheduleDelay));
			
			FilesAndStreams.saveSettings(configFile, config);
			
			LoggerFactory.getLogger(SocketConfig.class).info("saving settings to " + configFile + "... done.");
		}catch (Exception e){
			LoggerFactory.getLogger(SocketConfig.class).error("saving settings to " + configFile + "... failed!");
		}
	}

}
