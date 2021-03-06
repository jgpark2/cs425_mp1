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
	
	//Leader object that spawned this thread
	private CentralServer centralServer;
	
	//Structure to hold information from config file
	private NodeInfo [] nodesinfo;

	//Totally-ordered message queue
	private ArrayBlockingQueue<String> mqin;
	
	//FIFO channel queues going out to the Nodes
	public ArrayList< ArrayBlockingQueue<MessageType> > mqoutarr;
	
	//Tools to implement channel delay
	public Random r;
	//These are calculated once and saved to be more efficient
	private Double [] millismaxdelays;
	public int [] intmaxdelays;
	
	//This is 2D to represent all channels from a unique Id to a unique Id
	private MessageType [][] last;
	

	public MessageRouterThread(CentralServer centralServer,
			ArrayBlockingQueue<String> mqin, int mqmax) {
		
		//Initialization
		this.centralServer = centralServer;
		nodesinfo = centralServer.getNodesInfo();
		this.mqin = mqin;
		//Translate delay into necessary types
		millismaxdelays = new Double[4];
		intmaxdelays = new int[4];
		r = new Random();
		last = new MessageType[4][4];
		mqoutarr = new ArrayList< ArrayBlockingQueue<MessageType> >(4);
		
		//Translate into necessary formats only once to be efficient
		for (int i=0; i<4; i++) {
			millismaxdelays[i] = new Double(nodesinfo[i].max_delay*1000.0);
			intmaxdelays[i] = millismaxdelays[i].intValue();
			for (int j=0; j<4; j++)
				last[i][j] = new MessageType();
			mqoutarr.add(new ArrayBlockingQueue<MessageType>(mqmax));
		}
		
		new Thread(this, "RouteMessage").start();
	}
	
	
	/*
	 * This thread's main purpose is to get messages from the totally-ordered
	 * queue coming from all nodes trying to do broadcasts, and route them to
	 * the appropriate receiving node(s)
	 */
	public void run() {
		
		//Initialize sending connections with nodes
		for (int i=0; i<4; i++) {
			
			Socket servconn = null;
			while (servconn == null) {
				try {
					servconn = new Socket(nodesinfo[i].ip, nodesinfo[i].port);
    				System.out.print("Server: Now connected to "+nodesinfo[i].ip+":"+nodesinfo[i].port);
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
				String msg = mqin.take(); //has Node it received it from appended
				
				ArrayList<Integer> list = parseMessageForReceivingNodes(msg);
				int fromIdx = centralServer.getIndexFromId(msg.substring(msg.length()-1));
				msg = msg.substring(0, msg.length()-2); //take off space and fromId
				
				for (int i=0; i<list.size(); i++) {
	        		Integer idx = list.get(i);
	        		int recvIdx = idx.intValue();
	        		calculateDelayAndAddToQueue(recvIdx, fromIdx, msg);
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
	public void calculateDelayAndAddToQueue(int recvIdx, int fromIdx, String msg) {
		
		//parse the end of the message to figure out when it was timestamped
		int i = msg.length()-1;
		while (msg.charAt(i) != ' ') //move through latest timestamp
			i--;
		String timestr = msg.substring(i+1, msg.length());
		long ts = Long.parseLong(timestr);
		
		//Calculate random delay
		int randint = 0;
		if (intmaxdelays[recvIdx] > 0)
			randint = r.nextInt(intmaxdelays[recvIdx]); //random number of milliseconds
		
		//if last[recvIdx] is no longer in the channel, its ts will definitely be smaller
		Long tosendts = new Long(Math.max(ts + (long)randint, last[fromIdx][recvIdx].ts.longValue()));
		
		MessageType tosend = new MessageType(msg, tosendts);
		
		try {
			mqoutarr.get(recvIdx).put(tosend);
			last[fromIdx][recvIdx] = new MessageType(tosend);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	
	/*
	 * This method assumes that all messages sent to CentralServer have the
	 * timestamp of the invocation of the operation (whether req or ack)
	 * appended to the end of the message as a long type
	 * Does exactly the same thing as calculateDelayAndAddToQueue, but no delay
	 */
	private void noDelayAndAddToQueue(int recvIdx, int fromIdx, String msg) {
		
		MessageType tosend = new MessageType(msg, new Long(0));
		
		try {
			mqoutarr.get(recvIdx).put(tosend);
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
	 * The message has the Node it came from appended
	 */
	private ArrayList<Integer> parseMessageForReceivingNodes(String msg) {
		
		ArrayList<Integer> ret = new ArrayList<Integer>();
		
		//get key model <requestingnodeid> <requestnumber> <value> <reqorack> <timestamp>
		if (msg.substring(0, 4).compareToIgnoreCase("get ") == 0) {
			
			//Extract requestingnodeid
			int idx = 4;
			while (msg.charAt(idx) != ' ') //move past key
				idx++;
			idx += 3; //move past space,model,space
			String reqId = msg.substring(idx, idx+1);
			int reqIdx = centralServer.getIndexFromId(reqId);
			
			//Since the requestingNode is the only one that does anything with this message...
			ret.add(new Integer(reqIdx));
		}
		
		//delete key <timestamp>
		else if (msg.substring(0, 7).compareToIgnoreCase("delete ") == 0) {

			//Send to all, requestingnode will delete upon receipt of this
			for (int i=0; i<4; i++) {
				ret.add(new Integer(i));
			}
			
			//In addition, this means we should delete the key from globalData
			//Extract key out of msg
			StringBuilder builder = new StringBuilder();
			int i = 7;
			while (msg.charAt(i) != ' ') { //move through key
				builder.append(msg.charAt(i));
				i++;
			}
			String key = builder.toString();
			centralServer.globalData.remove(key);

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
			
			String tosend = new String(msg);
			
			//Extract requestingnodeid
			int idx = 7;
			while (tosend.charAt(idx) != ' ') //move past key
				idx++;
			idx++; //move past space between key and reqId
			
			String reqId = tosend.substring(idx, idx+1);
			int reqIdx = centralServer.getIndexFromId(reqId);
			
			int fromIdx = centralServer.getIndexFromId(tosend.substring(tosend.length()-1));
			tosend = tosend.substring(0, tosend.length()-2); //take off space and fromId
			
			if (tosend.lastIndexOf("req") != -1) { //request, send to all except requestingnode
				for (int i=0; i<4; i++) {
					if (i != reqIdx)
						noDelayAndAddToQueue(i, fromIdx, tosend);
				}
			}
			
			else { //ack, send to requestingnode
				noDelayAndAddToQueue(reqIdx, fromIdx, tosend);
			}
		}
		
		//writeglobal <key> <value> <associatedtimestamp> <timestamp>
		else if (msg.substring(0, 12).compareToIgnoreCase("writeglobal ") == 0) {

			//Extract key out of msg
			StringBuilder builder = new StringBuilder();
			int i = 12;
			while (msg.charAt(i) != ' ') { //move through key
				builder.append(msg.charAt(i));
				i++;
			}
			String key = builder.toString();
			
			//Extract value out of msg
			builder = new StringBuilder();
			i++; //move past space between key and value
			while (msg.charAt(i) != ' ') { //move through value
				builder.append(msg.charAt(i));
				i++;
			}
			String value = builder.toString();
			
			//Extract associated timestamp out of msg
			builder = new StringBuilder();
			i++; //move past space between value and timestamp
			while (i < msg.length() && msg.charAt(i) != ' ') { //move through timestamp
				builder.append(msg.charAt(i));
				i++;
			}
			long ts = Long.parseLong(builder.toString());
			
			Datum oldvalue = centralServer.globalData.get(key);
			
			if (oldvalue == null || oldvalue.timestamp < ts) {
				centralServer.globalData.put(key, new Datum(value,ts));
			}
			
			//this message is for the leader only; don't add anything to ret
		}
		
		return ret;
	}

}
