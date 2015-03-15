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

			String msg = "repair";
			
			Set<String> keyset = centralServer.globalData.keySet();
			Iterator<String> it = keyset.iterator();
			while (it.hasNext()) {
				String key = it.next();
				Datum value = centralServer.globalData.get(key);
				msg = msg + " " + key + " " + value.value + " " + value.timestamp;
			}
			
			long ts = System.currentTimeMillis();
			msg = msg + " " + ts; //add timstamp

			//This is subject to delays (Piazza @277)
			for (int i=0; i<4; i++) {
				//router.calculateDelayAndAddToQueue(i, 0, msg);
				
				//Calculate random delay
				int randint = 0;
				int intmaxdelay = router.intmaxdelays[i];
				if (intmaxdelay > 0)
					randint = router.r.nextInt(intmaxdelay);
				
				//if last[recvIdx] is no longer in the channel, its ts will definitely be smaller
				Long tosendts = new Long(ts + (long)randint);
				
				MessageType tosend = new MessageType(msg, tosendts);
				
				try {
					router.mqoutarr.get(i).put(tosend);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
	}

}
