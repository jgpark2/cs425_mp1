package mp1;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * MessageRouterThread: 1 per CentralLeader object
 * Functions as a separate thread running to create client connections
 * to become MessageDelayerThreads
 * Combines all messages received from MessageRelayThreads, parses them,
 *  and routes them to the correct MessageDelayerThread
 */
public class MessageRouterThread extends Thread {
	
	private CentralServer centralServer;
	private NodeInfo [] nodesinfo;

	private ArrayBlockingQueue<String> mqin;
	private ArrayList< ArrayBlockingQueue<MessageType> > mqoutarr;
	private int mqmax;
	
	private Random r;
	//These are calculated once and saved to be more efficient
	private Double [] millismaxdelays;
	private int [] intmaxdelays;
	private MessageType [] last;
	

	public MessageRouterThread(CentralServer centralServer,
			ArrayBlockingQueue<String> mqin, int mqmax) {
		this.centralServer = centralServer;
		nodesinfo = centralServer.getNodesInfo();
		this.mqin = mqin;
		this.mqmax = mqmax;
		
		//Translate delay into necessary types
		millismaxdelays = new Double[4];
		intmaxdelays = new int[4];
		r = new Random();
		last = new MessageType[4];
		mqoutarr = new ArrayList< ArrayBlockingQueue<MessageType> >(4);
		
		for (int i=0; i<4; i++) {
			millismaxdelays[i] = new Double(nodesinfo[i].max_delay*1000.0);
			intmaxdelays[i] = millismaxdelays[i].intValue();
			last[i] = new MessageType();
			mqoutarr.add(new ArrayBlockingQueue<MessageType>(mqmax));
		}
		
		new Thread(this, "RouteMessage").start();
	}
	
	
	public void run() {
		
		//Initialize sending connections with nodes
		for (int i=0; i<4; i++) {
			
			Socket servconn = null;
			while (servconn == null) {
				try {
					servconn = new Socket(nodesinfo[i].ip, nodesinfo[i].port);
    				System.out.print("Now connected to "+nodesinfo[i].ip+":"+nodesinfo[i].port);
    				System.out.println(" (node "+nodesinfo[i].id+")");
    				centralServer.setSendingThreadIndex(i,
    						new MessageDelayerThread(nodesinfo, -1, mqoutarr.get(i), nodesinfo[i].id, servconn));
    			} catch (Exception e) {
    				servconn=null;
    			}
			}
		}
		
		//Remove a message from the in-message queue and determine where it goes
		try {
			while (true) {
				String msg = mqin.take();
				
				ArrayList<Integer> list = parseMessageForReceivingNodes(msg);
				for (int i=0; i<list.size(); i++) {
	        		Integer idx = list.get(i);
	        		int recvIdx = idx.intValue();
	        		calculateDelayAndAddToQueue(recvIdx, msg);
	        	}
				
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	
	/*
	 * This method assumes that all messages sent to CentralServer have the
	 * timestamp of the invocation of the operation (whether req or ack)
	 * appended to the end of the message as a long type
	 */
	private void calculateDelayAndAddToQueue(int recvIdx, String msg) {
		
		//parse the end of the message to figure out when it was timestamped
		int i = msg.length()-1;
		while (msg.charAt(i) != ' ') //move through latest timestamp
			i--;
		String timestr = msg.substring(i+1, msg.length());
		long ts = Long.parseLong(timestr);
		
		//Calculate random delay
		int randint = r.nextInt(intmaxdelays[recvIdx]); //random number of milliseconds
		
		//if last[recvIdx] is no longer in the channel, its ts will definitely be smaller
		Long tosendts = new Long(Math.max(ts + (long)randint, last[recvIdx].ts.longValue()));
		
		MessageType tosend = new MessageType(msg, tosendts);
		
		try {
			mqoutarr.get(recvIdx).put(tosend);
			last[recvIdx] = tosend;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	
	/*
	 * Method to easily send a message to all Nodes
	 * This method is NOT used for routing normal messages because
	 * this does not calculate any delay
	 */
	public void addMessageToAllQueues(MessageType msg) {
		try {
        	for (Iterator< ArrayBlockingQueue<MessageType> > it = mqoutarr.iterator(); it.hasNext();) {
        		it.next().put(msg);
        	}
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
	}
	
	
	/*
	 * Takes a message from the in-message queue and decides which Nodes
	 * should receive it
	 */
	private ArrayList<Integer> parseMessageForReceivingNodes(String msg) {
		
		ArrayList<Integer> ret = new ArrayList<Integer>();
		
		//get key model <requestingnodeid> <requestnumber> <value> <reqorack> <timestamp>
		if (msg.substring(0, 4).compareToIgnoreCase("get ") == 0) {
			
			//All get messages that go through CentralServer are requests
			//(sequential consistency and linearizability need no acks, but do need own req)
			for (int i=0; i<4; i++) {
				ret.add(new Integer(i));
			}
		}
		
		//delete key <timestamp>
		else if (msg.substring(0, 7).compareToIgnoreCase("delete ") == 0) {

			//Send to all, requestingnode will delete upon receipt of this
			for (int i=0; i<4; i++) {
				ret.add(new Integer(i));
			}

		}
		
		//insert key value model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
		//update key value model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
		else if ( (msg.substring(0, 7).compareToIgnoreCase("insert ") == 0)
				|| ( msg.substring(0, 7).compareToIgnoreCase("update ") == 0 )) {
			
			//Extract requestingnodeid
			int idx = 7;
			while (msg.charAt(idx) != ' ') //move past key
				idx++;
			idx++; //move past space between key and value
			while (msg.charAt(idx) != ' ') //move past value
				idx++;
			idx+=3; //move past space,model,space
			String reqId = msg.substring(idx, idx+1);
			int reqIdx = centralServer.getIndexFromId(reqId);
			
			if (msg.lastIndexOf("req") != -1) { //request, send to all
				for (int i=0; i<4; i++)
					ret.add(new Integer(i));
			}
			
			else { //ack, send to requestingnode
				ret.add(new Integer(reqIdx));
			}
		}
		
		//search key <requestingnodeid> <requestnumber> <reqorack> <timestamp>
		else if (msg.substring(0, 7).compareToIgnoreCase("search ") == 0) {
			
			//Extract requestingnodeid
			int idx = 7;
			while (msg.charAt(idx) != ' ') //move past key
				idx++;
			idx++; //move past space between key and reqId
			
			String reqId = msg.substring(idx, idx+1);
			int reqIdx = centralServer.getIndexFromId(reqId);
			
			if (msg.lastIndexOf("req") != -1) { //request, send to all except requestingnode
				for (int i=0; i<4; i++) {
					if (i != reqIdx)
						ret.add(new Integer(i));
				}
			}
			
			else { //ack, send to requestingnode
				ret.add(new Integer(reqIdx));
			}
		}
		
		return ret;
	}

}
