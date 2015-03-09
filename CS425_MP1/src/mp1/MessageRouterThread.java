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
			String msg;
			while ((msg = mqin.take()).compareToIgnoreCase("exit") != 0) {
				
				ArrayList<Integer> list = parseMessageForReceivingNodes(msg);
				for (int i=0; i<list.size(); i++) {
	        		Integer idx = list.get(i);
	        		int recvIdx = idx.intValue();
	        		calculateDelayAndAddToQueue(recvIdx, msg);
	        	}
				
			}
			MessageType exitmsg = new MessageType(msg, new Long(0));
			addMessageToAllQueues(exitmsg); //exit message
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	
	private void calculateDelayAndAddToQueue(int recvIdx, String msg) {
		MessageType tosend = new MessageType(msg, new Long(0));
		
		//TODO: parse the end of the message to figure out when it was timestamped
		// instead of relying on system time
		Long ts = System.currentTimeMillis();
		
		//Calculate random delay
		int randint = r.nextInt(intmaxdelays[recvIdx]); //random number of milliseconds
		
		//if last[recvIdx] is no longer in the channel, its ts will definitely be smaller
		tosend.ts = new Long(Math.max(ts + (long)randint, last[recvIdx].ts.longValue()));
		
		try {
			mqoutarr.get(recvIdx).put(tosend);
			last[recvIdx] = tosend;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


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
		//TODO: finish parsing this exactly
		ArrayList<Integer> ret = new ArrayList<Integer>();
		
		//get key model <requestingnodeid> <requestnumber> <value> <reqorack> <timestamp>
		if (msg.substring(0, 4).compareToIgnoreCase("get ") == 0) {

			//At the moment, all get messages that go through CentralServer are requests
			//(sequential consistency and linearizability need no acks, but do need own req)
			for (int i=0; i<4; i++) {
				ret.add(new Integer(i));
			}
		}
		
		//delete key <requestingnodeid> <reqorack> <timestamp>
		else if (msg.substring(0, 7).compareToIgnoreCase("delete ") == 0) {
			
		}
		
		//insert key value model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
		else if (msg.substring(0, 7).compareToIgnoreCase("insert ") == 0) {
			
		}
		
		//update key value model <requestingnodeid> <requestnumber> <reqorack> <timestamp>
		else if (msg.substring(0, 7).compareToIgnoreCase("update ") == 0) {
			
		}
		
		return ret;
	}

}
