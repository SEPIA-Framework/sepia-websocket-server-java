package net.b07z.sepia.websockets.mqtt;

import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.tools.JSON;

public class Test_SepiaMqttClient {

	public static void main(String[] args) throws Exception{
		
		String mqttBroker = "ws://broker.hivemq.com:8000";
		
		SepiaMqttClient mqttClient = new SepiaMqttClient(mqttBroker, new SepiaMqttClientOptions()
				.setAutomaticReconnect(false)
				.setCleanSession(true)
				.setConnectionTimeout(6)
		);
		System.out.println("MQTT Client - connecting ...");
		mqttClient.connect();
		System.out.println("MQTT Client - publishing to 'sepia/mqtt-demo' ...");
		JSONObject jsonMessage = JSON.make(
				"deviceType", "light",
				"deviceIndex", "1",
				"roomType", "livingroom",
				"roomIndex", "1",
				"action", JSON.make(
					"type", "set",
					"value", "70",
					"valueType", "number_percent"
				)
		);
		mqttClient.publish("sepia/mqtt-demo", new SepiaMqttMessage(jsonMessage.toJSONString())
				.setQos(0)
				.setRetained(false)
		);
		System.out.println("MQTT Client - payload sent:\n");
		JSON.prettyPrint(jsonMessage);
		System.out.println("\nMQTT Client - disconnecting ... ");
		mqttClient.disconnect();
		System.out.println("MQTT Client - closing ... ");
		mqttClient.close();
		System.out.println("MQTT Client - closed.");
	}

}
