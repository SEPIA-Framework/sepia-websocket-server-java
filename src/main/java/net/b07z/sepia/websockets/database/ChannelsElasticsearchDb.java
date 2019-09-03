package net.b07z.sepia.websockets.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import net.b07z.sepia.websockets.common.SocketChannel;
import net.b07z.sepia.websockets.common.SocketConfig;

public class ChannelsElasticsearchDb implements ChannelsDatabase {

	private static final Logger log = LoggerFactory.getLogger(ChannelsElasticsearchDb.class);
	private static final String ES_CHANNELS_TYPE = "channels";
	private static final String ES_CHANNELS_PATH = SocketConfig.DB_CHAT_CHANNELS + "/" + ES_CHANNELS_TYPE;

	String esServerUrl = "";
	Elasticsearch es;
	
	public ChannelsElasticsearchDb(){
		this.esServerUrl = ConfigElasticSearch.getEndpoint(SocketConfig.defaultRegion);
		this.es = new Elasticsearch(this.esServerUrl);
	}

	public ChannelsElasticsearchDb(String serverUrl){
		this.esServerUrl = serverUrl;
		this.es = new Elasticsearch(this.esServerUrl);
	}
	
	@Override
	public boolean hasChannelWithId(String channelId) throws Exception {
		//get query for one result
		int resultsFrom = 0;
		int resultsSize = 1;

		List<QueryElement> matches = new ArrayList<>(); 
		matches.add(new QueryElement("channel_id", channelId));

		JSONObject queryJson = EsQueryBuilder.getBoolMustMatch(matches);
		JSON.put(queryJson, "from", resultsFrom);
		JSON.put(queryJson, "size", resultsSize);
		//System.out.println("query: " + queryJson.toJSONString());
		
		//query DB
		JSONObject data = this.es.searchByJson(ES_CHANNELS_PATH, queryJson.toJSONString());
		if (!Connectors.httpSuccess(data)){
			throw new Exception("hasChannelWithId failed due to access error with result: " + data.toJSONString());
		}else{
			JSONObject channelObj = JSON.getJObject(data, "hits");
			int total = JSON.getIntegerOrDefault(channelObj, "total", 0);
			return (total > 0);
		}
	}

	@Override
	public int storeChannel(SocketChannel socketChannel){
		int code = this.es.setItemData(SocketConfig.DB_CHAT_CHANNELS, ES_CHANNELS_TYPE, socketChannel.getChannelId(), socketChannel.getJson());
		if (code == 0 || code == 1){
			return code;
		}else{
			log.error("storeChannel - failed with code " + code + " for channelId: " + socketChannel.getChannelId());
			return 2;
		}
	}
	
	@Override
	public int updateChannel(String channelId, JSONObject updateData){
		//NOTE: channelId is ES ID as well
		return this.es.updateItemData(SocketConfig.DB_CHAT_CHANNELS, ES_CHANNELS_TYPE, channelId, updateData);
	}
	
	@Override
	public Map<String, SocketChannel> getAllChannles(boolean includeOtherServers){
		Map<String, SocketChannel> allChannelsById = new ConcurrentHashMap<>();
		
		JSONObject queryJson;
		if (includeOtherServers){
			queryJson = JSON.make(
					//"_source", JSON.makeArray("channel_id", "channel_name", "owner", "members"),
					"query", JSON.make("match_all", new JSONObject())
			);
		}else{
			List<QueryElement> matches = new ArrayList<>(); 
			matches.add(new QueryElement("server_id", SocketConfig.localName));
			queryJson = EsQueryBuilder.getBoolMustMatch(matches);
		}
		JSON.put(queryJson, "from", 0);
		JSON.put(queryJson, "size", SocketConfig.maxChannelsPerServer);
		
		JSONObject result = this.es.searchByJson(ES_CHANNELS_PATH, JSON.make(
				"from", 0, 
				"size", SocketConfig.maxChannelsPerServer,
				//"_source", JSON.makeArray("channel_id", "channel_name", "owner", "members"),
				"query", JSON.make("match_all", new JSONObject())
		).toJSONString());
		
		if (!Connectors.httpSuccess(result)){
			log.error("Failed to load channels from DB!");
			return null;
		}else{
			JSONArray channelArray = JSON.getJArray(result, new String[]{"hits", "hits"});
			if (Is.notNullOrEmpty(channelArray)){
				for (int i=0; i<channelArray.size(); i++){
					JSONObject channelRes = JSON.getJObject(channelArray, i);
					JSONObject channelData = JSON.getJObject(channelRes, "_source");
					SocketChannel sc = new SocketChannel(channelData);
					allChannelsById.put(sc.getChannelId(), sc);
				}
			}
		}
		log.info("Loaded " + allChannelsById.size() + " channels from DB.");
		return allChannelsById;
	}

	@Override
	public SocketChannel getChannelWithId(String channelId){
		//access directly by unique channel ID
		JSONObject data = new JSONObject();
		data = this.es.getItem(SocketConfig.DB_CHAT_CHANNELS, ES_CHANNELS_TYPE, channelId);
		
		if (Connectors.httpSuccess(data)){
			JSONObject channelData = JSON.getJObject(data, "_source");
			if (channelData == null){
				log.error("getChannelWithId - channel not found. ID: " + channelId);
				return null;
			}
			SocketChannel sc = new SocketChannel(channelData);
			if (!sc.getChannelId().equals(channelId)){
				log.error("getChannelWithId - found faulty channel entry with ID: " + channelId);
				return null;
			}
			return sc;
		}else{
			log.error("getChannelWithId - failed with result: " + data.toJSONString());
			return null;
		}
	}

	@Override
	public List<SocketChannel> getAllChannelsOwnedBy(String userId){
		//get query for one result
		int resultsFrom = 0;
		int resultsSize = Math.min(10000, SocketConfig.maxChannelsPerUser);		//TODO: leave this as is? 

		List<QueryElement> matches = new ArrayList<>(); 
		matches.add(new QueryElement("owner", userId));

		JSONObject queryJson = EsQueryBuilder.getBoolMustMatch(matches);
		JSON.put(queryJson, "from", resultsFrom);
		JSON.put(queryJson, "size", resultsSize);
		//System.out.println("query: " + queryJson.toJSONString());
		
		//query DB
		JSONObject data = this.es.searchByJson(ES_CHANNELS_PATH, queryJson.toJSONString());
		if (!Connectors.httpSuccess(data)){
			return null;
		}else{
			List<SocketChannel> channels = new ArrayList<>();
			JSONArray channelArray = JSON.getJArray(data, new String[]{"hits", "hits"});
			if (Is.notNullOrEmpty(channelArray)){
				for (int i=0; i<channelArray.size(); i++){
					JSONObject channelRes = JSON.getJObject(channelArray, i);
					JSONObject channelData = JSON.getJObject(channelRes, "_source");
					SocketChannel sc = new SocketChannel(channelData);
					if (!sc.getOwner().equals(userId)){
						log.error("getAllChannelsOwnedBy - found faulty channel entry with ID: " + sc.getChannelId());
					}else{
						channels.add(sc);
					}
				}
			}
			return channels;
		}
	}

	@Override
	public int removeChannel(String channelId){
		int code = this.es.deleteAnything(ES_CHANNELS_PATH + "/" + channelId);
		if (code == 0 || code == 1){
			return code;
		}else{
			log.error("removeChannel - failed with code " + code + " for channelId: " + channelId);
			return 2;
		}
	}
	
	@Override
	public long removeAllChannelsOfOwner(String userId) {
		List<QueryElement> matches = new ArrayList<>();
		matches.add(new QueryElement("owner", userId));

		JSONObject queryJson = EsQueryBuilder.getBoolMustMatch(matches);
		
		JSONObject data = new JSONObject();
		data = this.es.deleteByJson(ES_CHANNELS_PATH, queryJson.toJSONString());

		long deletedObjects = -1;
		if (Connectors.httpSuccess(data)){
			Object o = data.get("deleted");
			if (o != null){
				deletedObjects = (long) o;
			}
		}
		return deletedObjects;
	}
}
