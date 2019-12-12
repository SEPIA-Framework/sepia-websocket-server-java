package net.b07z.sepia.websockets.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

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
	 * Create MQTT client with default options.
	 * @param brokerAddress - address of MQTT broker, e.g. tcp://iot.eclipse.org:1883
	 * @throws Exception 
	 */
	public SepiaMqttClient(String brokerAddress) throws Exception {
		this.brokerAddress = brokerAddress;
		this.clientOptions = new SepiaMqttClientOptions();
		this.client = new MqttClient(this.brokerAddress, clientOptions.publisherId);
	}
	/**
	 * Create MQTT client with custom options.
	 * @param brokerAddress - address of MQTT broker, e.g. tcp://iot.eclipse.org:1883
	 * @param clientOptions - {@link SepiaMqttClient.SepiaMqttClientOptions}
	 * @throws Exception 
	 */
	public SepiaMqttClient(String brokerAddress, SepiaMqttClientOptions clientOptions) throws Exception {
		this.brokerAddress = brokerAddress;
		this.clientOptions = clientOptions;
		this.client = new MqttClient(this.brokerAddress, clientOptions.publisherId);
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
		this.client.connect(options);
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
