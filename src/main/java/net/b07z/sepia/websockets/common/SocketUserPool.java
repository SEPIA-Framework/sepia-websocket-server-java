package net.b07z.sepia.websockets.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.websocket.api.Session;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.core.tools.JSON;

/**
 * Manages the connected users.
 * 
 * @author Florian Quirin
 *
 */
public class SocketUserPool {
	
	private static Map<Session, SocketUser> userPool = new ConcurrentHashMap<>();
	private static Map<Session, JSONObject> pendingSession = new ConcurrentHashMap<>();
	
	public static void storeUser(SocketUser user){
		userPool.put(user.getUserSession(), user);
	}
	
	public static void removeUser(SocketUser user){
		userPool.remove(user.getUserSession());
	}
	
	public static void storePendingSession(Session session){
		pendingSession.put(session, JSON.make("pendingSince", System.currentTimeMillis()));
	}
	
	public static void removePendingSession(Session session){
		pendingSession.remove(session);
	}
	
	/**
	 * Get active user by id or null if no active user is present in pool.
	 */
	public static SocketUser getActiveUserById(String id){
		try{
			/*
			return userPool.entrySet().stream()
				.filter(e -> e.getValue().getUserId().equalsIgnoreCase(id))
				.map(Map.Entry::getValue)
				.findFirst().get();
			*/
			List<SocketUser> allUsers = getAllUsersById(id);
			for (SocketUser su : allUsers){
				if (su.isActive()){
					return su;
				}
			}
			return null;
			
		}catch (Exception e){
			return null;
		}
	}
	/**
	 * Get all users by id with open session or null if no user with this id is present in pool.
	 */
	public static List<SocketUser> getAllUsersById(String id){
		try{
			return userPool.entrySet().stream()
				.filter(e -> {
					//SocketUser su = e.getValue();
					//return (su.getUserSession().isOpen() && su.getUserId().equalsIgnoreCase(id));
					return (e.getValue().getUserId().equalsIgnoreCase(id));
				})
				.map(Map.Entry::getValue)
				.collect(Collectors.toList());

		}catch (Exception e){
			return null;
		}
	}
	/**
	 * Deactivated all users that are identical (same user ID, same device ID, same channel ID).
	 */
	public static List<SocketUser> setUsersWithSameIdInactive(SocketUser user){
		String userId = user.getUserId();
		Session excludeSession = user.getUserSession();
		String userChannel = user.getActiveChannel();
		String deviceId = user.getDeviceId();
		
		List<SocketUser> allUsers = getAllUsersById(userId);
		List<SocketUser> deactivatedUsers = new ArrayList<>();
		
		for (SocketUser su : allUsers){
			boolean isExcludeSession = (excludeSession != null)? (su.getUserSession().equals(excludeSession)) : false;
			if (!isExcludeSession){
				boolean isConflictChannel = (userChannel != null)? (su.getActiveChannel().equalsIgnoreCase(userChannel)) : true;
				boolean isSameDeviceId = (deviceId != null)? su.getDeviceId().equalsIgnoreCase(deviceId) : false;
				if (isConflictChannel && (isSameDeviceId || !SocketConfig.distinguishUsersByDeviceId) && su.isActive()){
					su.setInactive();
					deactivatedUsers.add(su);
				}
			}
		}
		return deactivatedUsers;
	}

	public static SocketUser getUserBySession(Session session){
		return userPool.get(session);
	}

	public static Collection<SocketUser> getAllUsers(){
		return userPool.values(); 		//TODO: iterations over this result are not thread-safe (I guess)
	}
}
