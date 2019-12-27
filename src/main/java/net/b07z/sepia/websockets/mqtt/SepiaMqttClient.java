package net.b07z.sepia.websockets.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

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
	 * @param brokerAddress - address of MQTT broker, e.g. tcp://iot.eclipse.org:1883
	 * @throws Exception 
	 */
	public SepiaMqttClient(String brokerAddress) throws Exception {
		this.brokerAddress = brokerAddress;
		this.clientOptions = new SepiaMqttClientOptions();
		this.client = new MqttClient(this.brokerAddress, clientOptions.publisherId, new MemoryPersistence());
	}
	/**
	 * Create MQTT client with custom options and in-memory persistence.
	 * @param brokerAddress - address of MQTT broker, e.g. tcp://iot.eclipse.org:1883
	 * @param clientOptions - {@link SepiaMqttClient.SepiaMqttClientOptions}
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
	 * Connect to broker with given options.
	 * @throws Exception
	 */
	public void connect() throws Exception {
		MqttConnectOptions options = new MqttConnectOptions();
		options.setAutomaticReconnect(this.clientOptions.automaticReconnect);
		options.setCleanSession(this.clientOptions.cleanSession);
		options.setConnectionTimeout(this.clientOptions.connectionTimeoutSec);
		//some defaults
		options.setMaxInflight(10);
		this.client.connect(options);
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

}
