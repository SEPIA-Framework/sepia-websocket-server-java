package net.b07z.sepia.websockets.common;

import java.util.Properties;

import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.tools.FilesAndStreams;

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
	public static String localName = "sepia-websocket-server";			//**user defined local server name (works as server device ID as well)
	public static String localSecret = "123456";						//**user defined secret to validate local server
	public static final String apiVersion = "v1.2.0";
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
	
	//General chat settings
	public static boolean distinguishUsersByDeviceId = true;		//allow 2 users with same ID to be active when device ID is different?
	public static boolean inUserChannelBroadcastOnlyToAssistantAndSelf = true;	//in user private channel don't broadcast to other devices
	
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
			//assistant
			//systemAssistantId = settings.getProperty("systemAssistantId");
			
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
			
			FilesAndStreams.saveSettings(configFile, config);
			
			LoggerFactory.getLogger(SocketConfig.class).info("saving settings to " + configFile + "... done.");
		}catch (Exception e){
			LoggerFactory.getLogger(SocketConfig.class).error("saving settings to " + configFile + "... failed!");
		}
	}

}
