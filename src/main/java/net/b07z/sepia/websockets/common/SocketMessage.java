package net.b07z.sepia.websockets.common;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import net.b07z.sepia.server.core.tools.JSON;

/**
 * Message sent via the webSocket connection.
 * 
 * @author Florian Quirin
 *
 */
public class SocketMessage {
	
	//statics
	private static AtomicLong idPool = new AtomicLong(0);
	
	public static enum TextType{
		chat,
		status
	}
	public static enum DataType{
		openText,			//a message that can be read by anything/anyone, has e.g. "parameters" and "credentials", but the server will send these only to trusty users (e.g. system assistant) 
		authenticate, 		//has no data entry by default, tells the user to authenticate himself
		joinChannel,		//has credentials for channel (channelId, channelKey)
		welcome,			//has optional data entry "username"
		byebye,				//has optional data entry "username"
		upgradeClient,		//has "role" as data entry
		assistAnswer,		//has data for assistant communication
		directCmd,			//has a direct cmd for assistant
		remoteAction,		//has a remote action like ASR trigger or hotkey submit
		errorMessage		//combined with TextType.status this message will be displayed as error line in channel (ignores normal status msg settings)
	}
	public static enum SenderType{
		user,
		server,
		assistant,
		client 		//to be used only in client itself
	}
	public static enum RemoteActionType{
		hotkey
	}

	private long id;				//TODO: should be combined with clientID. Can it overflow?, note: not yet submitted to client ...
	public String msgId = ""; 		//id defined by client to follow the message on reply
	public String channelId = ""; 	//broadcast to what channel?
	
	public String text;
	public String textType;
	public String html;
	public JSONObject data;
	
	public String sender;
	public String senderType;
	public String receiver;
	public String clientType;
	
	public long timeStampUNIX;
	public String timeStampHHmmss;
	
	public JSONArray userList;
	public Collection<SocketUser> userListCollection;
	
	/**
	 * Empty constructor to be filled manually. Only sets message id.
	 */
	public SocketMessage(){
		id = idPool.incrementAndGet();	if (id > (Long.MAX_VALUE - 1000)) idPool.set(Long.MIN_VALUE);
	};
	/**
	 * Create a message that can be sent over the webSocket connection.<br>
	 * This is a simple text message with a certain textType (e.g. chat or status). 
	 * @param channelId - server channel 
	 * @param sender - who sends it
	 * @param receiver - who shall receive it (empty for all)
	 * @param text - plain text
	 * @param textType - type of text (e.g. chat or status)
	 */
	public SocketMessage(String channelId, String sender, String receiver, String text, String textType){
		id = idPool.incrementAndGet();	if (id > (Long.MAX_VALUE - 1000)) idPool.set(Long.MIN_VALUE);
		this.channelId = channelId;
		this.sender = sender;
		this.receiver = receiver;
		this.text = text;
		this.textType = textType;
		
		this.timeStampUNIX = System.currentTimeMillis();
		this.timeStampHHmmss = new SimpleDateFormat("HH:mm:ss").format(new Date());
	}
	/**
	 * Create a message that can be sent over the webSocket connection.<br>
	 * This is a message formatted as HTML code. 
	 * @param channelId - server channel
	 * @param sender - who sends it
	 * @param receiver - who shall receive it (empty for all)
	 * @param html - HTML formatted message
	 */
	public SocketMessage(String channelId, String sender, String receiver, String html){
		id = idPool.incrementAndGet();	if (id > (Long.MAX_VALUE - 1000)) idPool.set(Long.MIN_VALUE);
		this.channelId = channelId;
		this.sender = sender;
		this.receiver = receiver;
		this.html = html;
				
		this.timeStampUNIX = System.currentTimeMillis();
		this.timeStampHHmmss = new SimpleDateFormat("HH:mm:ss").format(new Date());
	}
	/**
	 * Create a message that can be sent over the webSocket connection.<br>
	 * This is a message that uses a JSON object to submit data. 
	 * @param channelId - server channel
	 * @param sender - who sends it
	 * @param receiver - who shall receive it (empty for all)
	 * @param data - JSONObject data block
	 */
	public SocketMessage(String channelId, String sender, String receiver, JSONObject data){
		id = idPool.incrementAndGet();	if (id > (Long.MAX_VALUE - 1000)) idPool.set(Long.MIN_VALUE);
		this.channelId = channelId;
		this.sender = sender;
		this.receiver = receiver;
		this.data = data;
				
		this.timeStampUNIX = System.currentTimeMillis();
		this.timeStampHHmmss = new SimpleDateFormat("HH:mm:ss").format(new Date());
	}
	/**
	 * Create a message that can be sent over the webSocket connection.<br>
	 * Full version with all possible parameters. Usually you would want to use one of the type specific constructors.
	 * @param channelId - server channel
	 * @param sender - who sends the message?
	 * @param receiver - who shall receive the message?
	 * @param text - plain text message (optional)
	 * @param textType - type of text (e.g. chat or status) (optional)
	 * @param html - HTML formatted message (optional)
	 * @param data - JSONObject data block (optional)
	 * @param clientType - type of the client that sends/sent the message (optional)
	 * @param userList - list of users (optional)
	 */
	public SocketMessage(String channelId, String sender, String receiver, String text, String textType, String html, JSONObject data, String clientType, JSONArray userList){
		id = idPool.incrementAndGet();	if (id > (Long.MAX_VALUE - 1000)) idPool.set(Long.MIN_VALUE);
		this.channelId = channelId;
		this.sender = sender;
		this.receiver = receiver;
		this.text = text;
		this.textType = textType;
		this.html = html;
		this.data = data;
		this.clientType = clientType;
		this.userList = userList;
		
		this.timeStampUNIX = System.currentTimeMillis();
		this.timeStampHHmmss = new SimpleDateFormat("HH:mm:ss").format(new Date());
	}
	
	@Override
	public String toString(){
		return ("sender:" + sender + ",receiver:" + receiver 
				+ ",text-length:" + ((text == null)? "0" : text.length())
				+ ",data-length:" + ((data == null)? "0" : data.size()));
	}
	
	public void setUserList(JSONArray userList){
		this.userList = userList;
	}
	public void setUserList(Collection<SocketUser> userList){
		this.userListCollection = userList;
		JSONArray users = new JSONArray();
		for (SocketUser su : userList){
			JSON.add(users, su.getUserListEntry());
		}
		this.userList = users;
	}
	
	public void setClientType(String clientType){
		this.clientType = clientType;
	}
	
	public void setSenderType(String senderType){
		this.senderType = senderType;
	}
	
	public void setMessageId(String msgId){
		this.msgId = msgId;
	}
	
	public void setChannelId(String channelId){
		this.channelId = channelId;
	}
	
	/**
	 * Add data if data exists otherwise create data.
	 * @param key - field in data
	 * @param data - actual data to add at field
	 */
	public void addData(String key, Object value){
		if (this.data == null){
			this.data = new JSONObject(); 
		}
		JSON.add(this.data, key, value);
	}
	
	public long getId(){
		return id;
	}
	
	@SuppressWarnings("unchecked")
	/**
	 * Create a JSONObject from SocketMessage.
	 */
	public JSONObject getJSON(){
		JSONObject message = new JSONObject();
		message.put("msgId", msgId);
		message.put("channelId", channelId);
		message.put("sender", sender);
		if (senderType != null && !senderType.isEmpty()) message.put("senderType", senderType);
		message.put("timeUNIX", timeStampUNIX);
		message.put("time", timeStampHHmmss);
		if (receiver != null && !receiver.isEmpty()) message.put("receiver", receiver);
		if (text != null && !text.isEmpty()) message.put("text", text);
		if (textType != null && !textType.isEmpty()) message.put("textType", textType);
		if (html != null && !html.isEmpty()) message.put("html", html);
		if (data != null && !data.isEmpty()) message.put("data", data);
		if (clientType != null && !clientType.isEmpty())message.put("clientType", clientType);
		if (userList != null && !userList.isEmpty()){
			message.put("userList", userList);
		}
		return message;
	}
	
	/**
	 * Import a JSON string to make a SocketMessage.
	 */
	public static SocketMessage importJSON(String msg) throws Exception{
		JSONParser parser = new JSONParser();
		JSONObject msgJson = (JSONObject) parser.parse(msg);
		
		SocketMessage imported = new SocketMessage();
		
		imported.msgId = (String) msgJson.get("msgId");
		imported.channelId = (String) msgJson.get("channelId");
		imported.sender = (String) msgJson.get("sender"); 			//will be overwritten during broadcast to all to make sure the client cannot fake it
		imported.senderType = (String) msgJson.get("senderType");	//TODO: can be faked, do we care? Type server will be prohibited though
		imported.receiver = (String) msgJson.get("receiver");
		imported.timeStampUNIX = (long) msgJson.get("timeUNIX");
		imported.timeStampHHmmss = (String) msgJson.get("time");
		if (imported.timeStampHHmmss == null){
			imported.timeStampHHmmss = new SimpleDateFormat("HH:mm:ss").format(new Date(imported.timeStampUNIX));
		}
		imported.text = (String) msgJson.get("text");
		imported.textType = (String) msgJson.get("textType");
		imported.html = (String) msgJson.get("html");
		imported.data = (JSONObject) msgJson.get("data");
		imported.userList = (JSONArray) msgJson.get("userList");
		imported.clientType = (String) msgJson.get("clientType");
		
		return imported;
	}
}
