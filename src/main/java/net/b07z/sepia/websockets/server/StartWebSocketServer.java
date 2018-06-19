package net.b07z.sepia.websockets.server;
import static spark.Spark.*;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.data.Role;
import net.b07z.sepia.server.core.endpoints.CoreEndpoints;
import net.b07z.sepia.server.core.server.ConfigDefaults;
import net.b07z.sepia.server.core.server.RequestParameters;
import net.b07z.sepia.server.core.server.RequestPostParameters;
import net.b07z.sepia.server.core.server.SparkJavaFw;
import net.b07z.sepia.server.core.tools.DateTime;
import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.Timer;
import net.b07z.sepia.server.core.users.Account;
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketChannelPool;
import net.b07z.sepia.websockets.common.SocketConfig;
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
				"clusterKey", SocketConfig.clusterKey
		);
		ConfigDefaults.setupCoreTools(coreToolsConfig);
		//part 2
		long clusterTic = Timer.tic();
		JSONObject assistApiClusterData = ConfigDefaults.getAssistantClusterData();
		if (assistApiClusterData == null){
			new RuntimeException("Core-tools are NOT set properly! AssistAPI could not be reached!");
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
			new RuntimeException("Core-tools are NOT set properly!");
		}else{
			String assistantName = JSON.getString(assistApiClusterData, "assistantName");
			Debugger.println("Expecting assistant: " + assistantName + " (id: " + ConfigDefaults.defaultAssistantUserId + ")", 3);
		}
	}
	
	//SETUP DEFAULT CHANNELS
	public static void createDefaultChannels(){
		try {
			SocketChannelPool.createChannel("openWorld", SocketConfig.SERVERNAME, true);		//Open world
			
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
        
        webSocket("/messages", AbstractSocketHandler.class);
        webSocketIdleTimeoutMillis(SocketConfig.IDLE_TIMEOUT);
        
        //init(); //only needed when no REST end-points follow
        get("/online", (request, response) -> 			CoreEndpoints.onlineCheck(request, response));
		get("/ping", (request, response) -> 			CoreEndpoints.ping(request, response, SocketConfig.SERVERNAME));
		get("/validate", (request, response) -> 		CoreEndpoints.validateServer(request, response,	SocketConfig.SERVERNAME, 
															SocketConfig.apiVersion, SocketConfig.localName, SocketConfig.localSecret));
        post("/createChannel", (request, response) -> 	createChannel(request, response));
        post("/joinChannel", (request, response) -> 	joinChannel(request, response));
        //TODO:
        //getChannel, getAllChannels, getChannelData, deleteChannel
        
        //set access-control headers to enable CORS
		if (SocketConfig.allowCORS){
			SparkJavaFw.enableCORS("*", "*", "*");
		}
        
        awaitInitialization();
        
        createDefaultChannels();
        
        Debugger.println(DateTime.getLogDate() + " Welcome to the SEPIA webSocket server - port: " + SocketConfig.PORT, 3);
        
        SparkJavaFw.handleError();
        
		Debugger.println("Initialization complete, lets go!", 3);
    }
    
    /**
     * Create a new channel.
     */
    public static String createChannel(Request request, Response response){
    	//get parameters (or throw error)
    	RequestParameters params = new RequestPostParameters(request);
    	
    	//authenticate
    	Account userAccount = new Account();
		if (userAccount.authenticate(params)){
			//log.info("Authenticated: " + userAccount.getUserID() + ", roles: " + userAccount.getUserRoles()); 		//debug
			if (userAccount.hasRole(Role.developer.name())){
				//get parameters
				String channelId = params.getString("channelId");
				JSONArray initialMembers = params.getJsonArray("members");
				String owner = userAccount.getUserID();
				boolean isPublic = params.getBoolOrDefault("isPublic", false);
				boolean addAssistant = params.getBoolOrDefault("addAssistant", true);
				
				if (channelId == null || channelId.isEmpty()){
					JSONObject msgJSON = JSON.make("result", "fail", "error", "missing channelId");
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}else{
					//TODO: filter valid channel names
				}
				try{
					//create channel
					String channelKey = SocketChannelPool.createChannel(channelId, owner, isPublic);
					if (channelKey != null){
						List<String> members = new ArrayList<>();
						members.add(owner);
						if (initialMembers != null && !initialMembers.isEmpty()){
							for (Object o : initialMembers){
								members.add((String) o);
							}
						}
						SocketChannel sc = SocketChannelPool.getChannel(channelId);
						for (String s : members){
							sc.addUser(s, channelKey);
							//System.out.println("Member: " + s); 									//DEBUG
						}
						if (addAssistant){
							sc.addSystemDefaultAssistant(); 	//Add SEPIA too
							//System.out.println("Member: " + SocketConfig.systemAssistantId); 		//DEBUG
						}
						JSONObject msgJSON = JSON.make("result", "success",	"key", channelKey, "channelId", channelId);
						return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
					
					}else{
						//error
						JSONObject msgJSON = JSON.make("result", "fail", "error", "creation failed! Maybe already exists?");
						return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
					}
				}catch (Exception e){
					//error
					JSONObject msgJSON = JSON.make("result", "fail", "error", e.getMessage());
					return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
				}	
			}else{
				//refuse
				JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized, missing required role");
				return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 404);
			}
		}else{
			//refuse
			JSONObject msgJSON = JSON.make("result", "fail", "error", "not authorized");
			return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 404);
		}
    }
    /**
     * Join a channel.
     */
    public static String joinChannel(Request request, Response response){
    	//TODO:
    	// - authenticate
    	// - get parameters (channelId, key, user)
    	// - return success
    	// ...
    	//get parameters (or throw error)
    	//RequestParameters params = new RequestPostParameters(request);
    	
    	JSONObject msgJSON = JSON.make("result", "fail", "error", "not yet implemented");
    	return SparkJavaFw.returnResult(request, response, msgJSON.toJSONString(), 200);
    }

}
