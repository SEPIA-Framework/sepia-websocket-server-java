package net.b07z.sepia.websockets.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.b07z.sepia.server.core.database.Elasticsearch;
import net.b07z.sepia.server.core.tools.Connectors;
import net.b07z.sepia.server.core.tools.EsQueryBuilder;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.EsQueryBuilder.QueryElement;
import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.websockets.common.SocketConfig;
import net.b07z.sepia.websockets.common.SocketMessage;

public class ChatsElasticsearchDb implements ChatsDatabase {

	private static final Logger log = LoggerFactory.getLogger(ChatsElasticsearchDb.class);
	private static final String ES_CHAT_COMMON_TYPE = "all";
	private static final String ES_CHAT_MESSAGES_PATH = SocketConfig.DB_CHAT_MESSAGES + "/" + ES_CHAT_COMMON_TYPE;
	//private static final String ES_CHAT_USERS_PATH = SocketConfig.DB_CHAT_USERS + "/" + ES_CHAT_COMMON_TYPE;

	String esServerUrl = "";
	Elasticsearch es;
	
	public ChatsElasticsearchDb(){
		this.esServerUrl = ConfigElasticSearch.getEndpoint(SocketConfig.defaultRegion);
		this.es = new Elasticsearch(this.esServerUrl);
	}

	public ChatsElasticsearchDb(String serverUrl){
		this.esServerUrl = serverUrl;
		this.es = new Elasticsearch(this.esServerUrl);
	}

	@Override
	public int updateChannelsWithMissedMessagesForUser(String userId, Set<String> channelIds){
		//NOTE: userId is ES ID as well
		JSONObject updateData = JSON.make(
				"userId", userId,
				"checkChannels", channelIds
		);
		return this.es.updateItemData(SocketConfig.DB_CHAT_USERS, ES_CHAT_COMMON_TYPE, userId, updateData);
	}

	@Override
	public Set<String> getAllChannelsWithMissedMassegesForUser(String userId){
		//call ES directly with ID
		JSONObject data = this.es.getItem(SocketConfig.DB_CHAT_USERS, ES_CHAT_COMMON_TYPE, userId);
		//success
		if (Connectors.httpSuccess(data)){
			try{
				Set<String> channelIdSet = new ConcurrentSkipListSet<String>();
				
				JSONObject userData = JSON.getJObject(data, "_source");
				if (userData == null){
					//this should mean that there is no data
					return channelIdSet;
				}
				JSONArray channelIds = JSON.getJArray(userData, "checkChannels");
				if (channelIds != null){
					for (int i=0; i<channelIds.size(); i++){
						channelIdSet.add((String) channelIds.get(i)); 
					}
				}
				return channelIdSet;
			
			}catch (Exception e){
				log.error("getAllChannelsWithMissedMassegesForUser - failed with error: " + e.getMessage());
				return null;
			}
		//fail
		}else{
			log.error("getAllChannelsWithMissedMassegesForUser - failed with result: " + data.toJSONString());
			return null;
		}
	}

	@Override
	public int storeChannelMessage(JSONObject msg){
		JSONObject res = this.es.setAnyItemData(SocketConfig.DB_CHAT_MESSAGES, ES_CHAT_COMMON_TYPE, msg);
		int code = JSON.getIntegerOrDefault(res, "code", -1);
		if (code == 0 || code == 1){
			return code;
		}else{
			log.error("storeChannelMessage - failed with code " + code);
			return 2;
		}
	}

	@Override
	public int removeOldChannelMessages(String channelId, long olderThanUnix){
		//build delete query
		JSONObject jsonQuery;
		List<QueryElement> matches = new ArrayList<>();
		List<QueryElement> ranges = new ArrayList<>();
		matches.add(new QueryElement("channelId", channelId));
		ranges.add(new QueryElement("timeUNIX", JSON.make("lt", olderThanUnix)));
		jsonQuery = EsQueryBuilder.getBoolMustAndRangeMatch(matches, ranges);
		
		JSONObject result = this.es.deleteByJson(ES_CHAT_MESSAGES_PATH, jsonQuery.toJSONString());
		//System.out.println("result: " + result);
		if (Connectors.httpSuccess(result)){
			try{
				//get total deleted entries
				int total = JSON.getIntegerOrDefault(result, "total", -1);
				if (total == -1){
					log.error("removeOldChannelMessages - failed with result " + result);
					return -1;
				}else{
					return total;
				}
			}catch (Exception e){
				log.error("removeOldChannelMessages - failed with error: " + e.getMessage());
				return -1;
			}
		}else{
			log.error("removeOldChannelMessages - failed with result " + result);
			return -1;
		}
	}

	@Override
	public List<SocketMessage> getAllMessagesOfChannel(String channelId, long notOlderThanUNIX){
		//build delete query
		JSONObject jsonQuery;
		List<QueryElement> matches = new ArrayList<>();
		List<QueryElement> ranges = new ArrayList<>();
		matches.add(new QueryElement("channelId", channelId));
		ranges.add(new QueryElement("timeUNIX", JSON.make("gte", notOlderThanUNIX)));
		jsonQuery = EsQueryBuilder.getBoolMustAndRangeMatch(matches, ranges);
		//limit results
		JSON.put(jsonQuery, "from", 0);
		JSON.put(jsonQuery, "size", 1000);	//SocketConfig.storeMessagesPerChannel
		
		JSONObject result = this.es.searchByJson(ES_CHAT_MESSAGES_PATH, jsonQuery.toJSONString());
		//System.out.println("getAllMessagesOfChannel: " + result);
		
		if (!Connectors.httpSuccess(result)){
			log.error("getAllMessagesOfChannel - Failed to load messages from DB!");
			return null;
		}else{
			try{
				List<SocketMessage> allMessages = new ArrayList<>();
				JSONArray messageArray = JSON.getJArray(result, new String[]{"hits", "hits"});
				if (Is.notNullOrEmpty(messageArray)){
					for (int i=0; i<messageArray.size(); i++){
						JSONObject messageRes = JSON.getJObject(messageArray, i);
						JSONObject messageData = JSON.getJObject(messageRes, "_source");
						SocketMessage msg;
						msg = SocketMessage.importJSON(messageData);
						allMessages.add(msg);
					}
				}
				log.info("getAllMessagesOfChannel - Loaded " + allMessages.size() + " messages from DB.");
				return allMessages;
			
			}catch (Exception e){
				log.error("getAllMessagesOfChannel - Failed to load messages from DB with error: " + e);
				return null;
			}
		}
	}
}
