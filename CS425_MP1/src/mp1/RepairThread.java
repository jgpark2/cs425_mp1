package mp1;

import java.util.Iterator;
import java.util.Set;

/*
 * RepairThread: 1 per CentralServer object
 * Sends a periodic repair message to all Nodes to make the inconsistency repair
 * Periodic message is of form:
 * "repair <key> <value> <associatedtimestamp> <key> <value> <associatedtimestamp> ... "
 * This message is then given to
 */
public class RepairThread extends Thread {
	
	private CentralServer centralServer;
	private MessageRouterThread router;

	
	public RepairThread(CentralServer centralServer) {
		this.centralServer = centralServer;
		this.router = centralServer.getConnectionRouterThread();
		
		new Thread(this, "RepairInitiater").start();
	}
	
	
	public void run() {
		
		while (true) {
			try {
				Thread.sleep(10 * 1000); //10 seconds
			} catch (InterruptedException e) {
				System.out.println("Interrupted while Repair was sleeping");
				e.printStackTrace();
				return;
			}
			
			//Since this message won't be delayed, the timestamp doesn't matter
			MessageType msg = new MessageType("repair", new Long(0));
			
			Set<String> keyset = centralServer.globalData.keySet();
			Iterator<String> it = keyset.iterator();
			while (it.hasNext()) {
				String key = it.next();
				Datum value = centralServer.globalData.get(key);
				msg.msg = msg.msg + " " + key + " " + value.value + " " + value.timestamp;
			}

			//This is not subject to delay because it is initiated by "the system"
			router.addMessageToAllQueues(msg);
		}
		
	}

}
