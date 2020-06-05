package net.b07z.sepia.websockets.mqtt;

import java.util.function.Consumer;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;

/**
 * Abstraction layer for MQTT client.
 * 
 * @author Florian Quirin
 * 
 */
public class SepiaMqttClient {
	
	private String brokerAddress;
	private IMqttClient client;
	private SepiaMqttClientOptions clientOptions;
	
	/**
	 * Create MQTT client with default options and in-memory persistence.
	 * @param brokerAddress - address of MQTT broker, e.g. tcp://iot.eclipse.org:1883 or ws://broker.hivemq.com:8000
	 * @throws Exception 
	 */
	public SepiaMqttClient(String brokerAddress) throws Exception {
		this.brokerAddress = brokerAddress;
		this.clientOptions = new SepiaMqttClientOptions();
		this.client = new MqttClient(this.brokerAddress, clientOptions.publisherId, new MemoryPersistence());
	}
	/**
	 * Create MQTT client with custom options and in-memory persistence.
	 * @param brokerAddress - address of MQTT broker, e.g. tcp://iot.eclipse.org:1883 or ws://broker.hivemq.com:8000
	 * @param clientOptions - {@link SepiaMqttClientOptions}
	 * @throws Exception 
	 */
	public SepiaMqttClient(String brokerAddress, SepiaMqttClientOptions clientOptions) throws Exception {
		this.brokerAddress = brokerAddress;
		this.clientOptions = clientOptions;
		this.client = new MqttClient(this.brokerAddress, clientOptions.publisherId, new MemoryPersistence());
	}
	
	/**
	 * Retrieve options set or created during construction.
	 */
	public SepiaMqttClientOptions getOptions(){
		return this.clientOptions;
	}
	
	/**
	 * Connect to broker with given options. This is a blocking method that returns when connected (or on error).
	 * @throws Exception
	 */
	public void connect() throws Exception {
		MqttConnectOptions options = new MqttConnectOptions();
		options.setAutomaticReconnect(this.clientOptions.automaticReconnect);
		options.setCleanSession(this.clientOptions.cleanSession);
		options.setConnectionTimeout(this.clientOptions.connectionTimeoutSec);
		if (Is.notNullOrEmpty(this.clientOptions.userName)){
			options.setUserName(this.clientOptions.userName);
		}
		if (Is.notNullOrEmpty(this.clientOptions.password)){
			options.setPassword(this.clientOptions.password.toCharArray());
		}
		//some defaults
		options.setMaxInflight(10);
		this.client.connect(options);
	}
	
	/**
	 * Is client connected to server?
	 */
	public boolean isConnected(){
		return this.client.isConnected();
	}
	
	/**
	 * Disconnects from the server. An attempt is made to quiesce the client allowing outstanding work to complete before disconnecting. 
	 * It will wait for a maximum of 30 seconds for work to quiesce before disconnecting. 
	 * This is a blocking method that returns once disconnect completes.
	 * @throws Exception - if a problem is encountered while disconnecting
	 */
	public void disconnect() throws Exception {
		this.client.disconnect();
	}
	
	/**
	 * Close the client Releases all resource associated with the client. 
	 * After the client has been closed it cannot be reused. For instance attempts to connect will fail.
	 * @throws Exception - if the client is not disconnected
	 */
	public void close() throws Exception {
		this.client.close();
	}
	
	/**
	 * Publish a MQTT message to broker.
	 * @param topic
	 * @param message
	 * @throws Exception
	 */
	public void publish(String topic, SepiaMqttMessage message) throws Exception {
		MqttMessage msg = new MqttMessage();
		msg.setPayload(message.getPayload());
        msg.setQos(message.getQos());
        msg.setRetained(message.getRetained());
		this.client.publish(topic, msg);
	}

	/**
	 * Subscribe to a topic and register a message handler.
	 * @param topic - any MQTT topic (can include wildcards)
	 * @param callbackHandler - consumer to handle JSON response (keys: id, topic, payload)
	 * @throws Exception
	 */
	public void subscribe(String topic, Consumer<JSONObject> callbackHandler) throws Exception {
		this.client.subscribe(topic, new IMqttMessageListener(){
			@Override
			public void messageArrived(String top, MqttMessage message) throws Exception{
				String msg = message.toString();
				JSONObject payload;
				if (msg != null && msg.getClass().equals(String.class)){
					try{
						if (msg.startsWith("{")){
							//parse to JSONObject
							payload = JSON.parseStringOrFail(msg);
						}else if (msg.startsWith("[")){
							//parse to JSONArray
							JSONArray data = JSON.parseStringToArrayOrFail(msg);
							payload = JSON.make("data", data);
						}else{
							payload = JSON.make("message", msg);
						}
					}catch(Exception e){
						payload = JSON.make("message", msg);
					}
				}else{
					//failed to convert MqttMessage
					payload = new JSONObject();
				}
				JSONObject jo = JSON.make(
					"topic", top,
					"id", message.getId(),
					"payload", payload
				);
				callbackHandler.accept(jo);
			}
		});
	}
	
	/**
	 * Unsubscribe from topic or fail
	 * @param topic - topic to unsubscribe from
	 * @throws MqttException
	 */
	public void unsubscribe(String topic) throws MqttException {
		this.client.unsubscribe(topic);
	}
}
