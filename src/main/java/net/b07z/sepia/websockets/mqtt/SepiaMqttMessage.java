package net.b07z.sepia.websockets.mqtt;

import java.nio.charset.StandardCharsets;

import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * Abstraction layer for MQTT messages.
 * 
 * @author Florian Quirin
 *
 */
public class SepiaMqttMessage {

	private byte[] payload;        
	private int qualityOfService = 1;
	private boolean retained = false;
	
	public SepiaMqttMessage(byte[] payloadBytes){
		this.payload = payloadBytes; 
	}
	
	public SepiaMqttMessage(String payloadStringUtf8){
		this.payload = payloadStringUtf8.getBytes(StandardCharsets.UTF_8); 
	}
	
	/**
	 * Sets the quality of service for this message.<br> 
	 * <li>Quality of Service 0 - indicates that a message should be delivered at most once (zero or one times). Fastest QoS, also known as "fire and forget".</li>
	 * <li>Quality of Service 1 - indicates that a message should be delivered at least once (one or more times). The message will be persisted to disk if the application can supply means of persistence using connect options. This is the default QoS.</li>
	 * <li>Quality of Service 2 - indicates that a message should be delivered once. Same as one but will make sure the message is not sent more than once.</li><br>
	 * If persistence is not configured, QoS 1 and 2 messages might still be delivered as the client will hold state in memory until shutdown or failure.
	 * @param qualityOfService - the "quality of service" to use. Set to 0, 1, 2.
	 */
	public SepiaMqttMessage setQos(int qualityOfService){
		this.qualityOfService = qualityOfService;
		new MqttMessage().setQos(qualityOfService);
		return this;
	}
	public int getQos(){
		return this.qualityOfService;
	}
	
	/**
	 * Whether or not the publish message should be retained by the messaging engine. 
	 * Sending a message with retained set to true and with an empty byte array as the payload e.g. new byte[0] will clear the retained message from the server. 
	 * The default value is false.
	 * @param retained - whether or not the messaging engine should retain the message
	 */
	public SepiaMqttMessage setRetained(boolean retained){
		this.retained = retained;
		return this;
	}
	public boolean getRetained(){
		return this.retained;
	}
	
	public byte[] getPayload(){
		return this.payload;
	}
}
