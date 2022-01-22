package net.b07z.sepia.websockets.server;
import static spark.Spark.*;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.endpoints.CoreEndpoints;
import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.server.RequestGetOrFormParameters;
import net.b07z.sepia.server.core.server.RequestParameters;
import net.b07z.sepia.server.core.server.SparkJavaFw;
import net.b07z.sepia.server.core.tools.DateTime;
import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.Timer;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.database.ChannelsDatabase;
import net.b07z.sepia.websockets.endpoints.ChannelManager;
import net.b07z.sepia.websockets.endpoints.ClientManager;
import spark.Request;
import spark.Response;

public class StartWebSocketServer {
	
	static Logger log = LoggerFactory.getLogger(StartWebSocketServer.class);
	
	//stuff
	public static String startGMT = "";
	public static String serverType = "";
	
	public static final String LIVE_SERVER = "live";
	public static final String TEST_SERVER = "test";
	public static final String CUSTOM_SERVER = "custom";
	
	//SETTINGS
	public static void loadSettings(String[] args) {
		//check arguments
		serverType = TEST_SERVER;
		for (String arg : args){
			if (arg.equals("--test")){
				//Test system
				serverType = TEST_SERVER;
			}else if (arg.equals("--live")){
				//Live system
				serverType = LIVE_SERVER;
			}else if (arg.equals("--my") || arg.equals("--custom")){
				//Custom system
				serverType = CUSTOM_SERVER;
			}else if (arg.equals("--ssl")){
				//SSL
				SocketConfig.isSSL = true;
			}else if (arg.startsWith("keystorePwd=")){
				//Java key-store password - TODO: maybe not the best way to load the pwd ...
				SocketConfig.keystorePwd = arg.replaceFirst(".*?=", "").trim();
			}
		}
		//set security
		if (SocketConfig.isSSL){
			secure("Xtensions/SSL/ssl-keystore.jks", SocketConfig.keystorePwd, null, null);
		}
		//load settings
		if (serverType.equals(TEST_SERVER)){
			log.info("-- Running " + SocketConfig.SERVERNAME + " with TEST settings --");
			SocketConfig.loadSettings("Xtensions/websocket.test.properties");
		}else if (serverType.equals(CUSTOM_SERVER)){
			log.info("-- Running " + SocketConfig.SERVERNAME + " with CUSTOM settings --");
			SocketConfig.loadSettings("Xtensions/websocket.custom.properties");
		}else{
			log.info("-- Running " + SocketConfig.SERVERNAME + " with LIVE settings --");
			SocketConfig.loadSettings("Xtensions/websocket.properties");
		}
		
		staticFiles.location("/public"); 	//index.html is served at localhost:PORT
        //staticFiles.expireTime(7200);
		
		//SETUP CORE-TOOLS
		JSONObject coreToolsConfig;
		//part 1
		coreToolsConfig = JSON.make(
				"defaultAssistAPI", SocketConfig.assistAPI,
				"defaultTeachAPI", SocketConfig.teachAPI,
				"clusterKey", SocketConfig.clusterKey,
				"privacyPolicy", SocketConfig.privacyPolicyLink
		);
		ConfigDefaults.setupCoreTools(coreToolsConfig);
		//part 2
		long clusterTic = Timer.tic();
		JSONObject assistApiClusterData = ConfigDefaults.getAssistantClusterData();
		if (assistApiClusterData == null){
			throw new RuntimeException("Core-tools are NOT set properly! AssistAPI could not be reached!");
		}else{
			log.info("Received cluster-data from AssistAPI after " + Timer.toc(clusterTic) + "ms");
		}
		coreToolsConfig = JSON.make(
				"defaultAssistantUserId", JSON.getString(assistApiClusterData, "assistantUserId")
		);
		//common micro-services API-Keys
		//...JSON.put(coreToolsConfig, "...ApiKey", ...);
		ConfigDefaults.setupCoreTools(coreToolsConfig);
		
		//Check core-tools settings
		if (!ConfigDefaults.areCoreToolsSet()){
			throw new RuntimeException("Core-tools are NOT set properly!");
		}else{
			String assistantName = JSON.getString(assistApiClusterData, "assistantName");
			Debugger.println("Expecting assistant: " + assistantName + " (id: " + ConfigDefaults.defaultAssistantUserId + ")", 3);
		}
	}
	
	//SETUP DEFAULT CHANNELS
	public static void createDefaultChannels(){
		try {
			//Load from database
			ChannelsDatabase channelsDb = SocketConfig.getDefaultChannelsDatabase();
			boolean includeOtherServers = false;
			Map<String, SocketChannel> channelPool = channelsDb.getAllChannles(includeOtherServers);
			
			//Check if we got a result
			if (channelPool != null){
				SocketChannelPool.setPool(channelPool);
			}
			
			//Open world
			if (!SocketChannelPool.hasChannelId(SocketChannel.OPEN_WORLD)){
				SocketChannelPool.createChannel(
						SocketChannel.OPEN_WORLD, SocketConfig.SERVERNAME, true, "Open World", new HashSet<String>(), false
				);
			}
			
		} catch (Exception e) {
			log.error("One or more default channels could not be created!");
			e.printStackTrace();
		}
	}

    //MAIN
	public static void main(String[] args) {
    	//load settings
    	loadSettings(args);
        
        //port
    	port(SocketConfig.PORT);
        
        SocketServer server = new SepiaSocketHandler();
        AbstractSocketHandler.server = server;
        
        webSocket("/messages/", AbstractSocketHandler.class); 		//NOTE: it HAS TO end with "/"
        webSocketIdleTimeoutMillis(SocketConfig.IDLE_TIMEOUT);
        //init(); //only needed when no REST end-points follow
        
        get("/online", (request, response) -> 			CoreEndpoints.onlineCheck(request, response));
		get("/ping", (request, response) -> 			CoreEndpoints.ping(request, response, SocketConfig.SERVERNAME));
		get("/validate", (request, response) -> 		CoreEndpoints.validateServer(request, response,	SocketConfig.SERVERNAME, 
															SocketConfig.apiVersion, SocketConfig.localName, SocketConfig.localSecret));
		post("/hello", StartWebSocketServer::helloWorld);
		
        post("/createChannel", (request, response) -> 	ChannelManager.createChannel(request, response));
        post("/joinChannel", (request, response) -> 	ChannelManager.joinChannel(request, response));
        post("/deleteChannel", (request, response) -> 	ChannelManager.deleteChannel(request, response));
        post("/getAvailableChannels", (request, response) -> 	ChannelManager.getAvailableChannels(request, response));
        post("/getChannelHistoryStatistic", (request, response) -> 	ChannelManager.getChannelHistoryStatistic(request, response));
        post("/removeOutdatedChannelMessages", (request, response) -> 	ChannelManager.removeOutdatedChannelMessages(request, response));
        //TODO: getChannel, getChannelData
        
        post("/getOwnClientConnections", (request, response) -> 	ClientManager.getClientConnections(request, response));
        post("/refreshClientConnections", (request, response) -> 	ClientManager.refreshClientConnections(request, response));
        
        //usually requested via socket connection:
        post("/getChannelsWithMissedMessages", (request, response) -> 	ChannelManager.getChannelsWithMissedMessages(request, response));
                
        //set access-control headers to enable CORS
		if (SocketConfig.allowCORS){
			SparkJavaFw.enableCORS("*", "*", "*");
		}
        
        awaitInitialization();
        
        createDefaultChannels();
        
        Debugger.println("Welcome to the SEPIA Chat-Server " + SocketConfig.apiVersion + " (" + serverType + ") - port: " + SocketConfig.PORT, 3);
		startGMT = DateTime.getGMT(new Date(), "dd.MM.yyyy' - 'HH:mm:ss' - GMT'");
		Debugger.println("Date: " + startGMT, 3);
        
        SparkJavaFw.handleError();
        
		Debugger.println("Initialization complete, lets go!", 3);
    }
	
	//hello and statistics end-point
	private static String helloWorld(Request request, Response response){
		//time now
		Date date = new Date();
		String nowGMT = DateTime.getGMT(date, "dd.MM.yyyy' - 'HH:mm:ss' - GMT'");
		
		//get parameters (or throw error) - NOTE: historically this is a bit inconsistent with the other APIs
		RequestParameters params = new RequestGetOrFormParameters(request);
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			String reply;
			if (userAccount.hasRole(Role.developer.name())){
				//stats
				reply = "Hello World!"
						+ "<br><br>"
						+ "Stats:<br>" +
								"<br>api: " + SocketConfig.apiVersion +
								"<br>started: " + startGMT +
								"<br>now: " + nowGMT +
								"<br>host: " + request.host() +
								"<br>url: " + request.url() + "<br><br>" +
								Statistics.getInfo();
			}else{
				reply = "Hello World!";
			}
			JSONObject msg = JSON.make("result", "success", "reply", reply);
			return SparkJavaFw.returnResult(request, response, msg.toJSONString(), 200);
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 403);
		}
	}
}
