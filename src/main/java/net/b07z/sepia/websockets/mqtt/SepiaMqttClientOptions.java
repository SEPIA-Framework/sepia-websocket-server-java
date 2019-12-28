package net.b07z.sepia.websockets.mqtt;

import java.util.UUID;

/**
 * MQTT client options used before connecting.
 * 
 * @author Florian Quirin
 * 
 */
public class SepiaMqttClientOptions {
	public boolean automaticReconnect = true;
	public boolean cleanSession = true;
	public int connectionTimeoutSec = 10;
	public String publisherId;
	public String userName;
	public String password;
	
	/**
	 * Create new MQTT client options with random publisher ID.
	 */
	public SepiaMqttClientOptions(){
		this.publisherId = UUID.randomUUID().toString();	//alternative: MqttClient.generateClientId()
	}
	/**
	 * Create new MQTT client options with given publisher ID.
	 */
	public SepiaMqttClientOptions(String publisherId){
		this.publisherId = publisherId;
	}
	
	/**
	 * Automatically reconnect in the event of network failure?
	 * @param automaticReconnect
	 */
	public SepiaMqttClientOptions setAutomaticReconnect(boolean automaticReconnect){
		this.automaticReconnect = automaticReconnect;
		return this;
	}
	/**
	 * Discard unsent messages from a previous run?
	 * @param cleanSession
	 */
	public SepiaMqttClientOptions setCleanSession(boolean cleanSession){
		this.cleanSession = cleanSession;
		return this;
	}
	/**
	 * Connection timeout in seconds.
	 * @param connectionTimeoutSec
	 */
	public SepiaMqttClientOptions setConnectionTimeout(int connectionTimeoutSec){
		this.connectionTimeoutSec = connectionTimeoutSec;
		return this;
	}
	
	/**
	 * Set username for authentication.
	 * @param username
	 */
	public SepiaMqttClientOptions setUserName(String userName){
		this.userName = userName;
		return this;
	}
	/**
	 * Set password for authentication.
	 * @param password
	 */
	public SepiaMqttClientOptions setPassword(String password){
		this.password = password;
		return this;
	}
}
