package mp1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * MessageRouterThread: 1 per CentralLeader object
 * Functions as a separate thread running to create client connections
 * to become MessageSenderThreads
 * Combines all messages received from MessageRouterThreads, parses them,
 *  and routes them to the correct MessageSenderThread
 */
public class MessageRouterThread extends Thread {
	
	private CentralServer centralServer;
	private NodeInfo [] nodesinfo;

	private ArrayBlockingQueue<String> mqin;
	private ArrayList< ArrayBlockingQueue<String> > mqoutarr;
	private int mqmax;
	

	public MessageRouterThread(CentralServer centralServer,
			ArrayBlockingQueue<String> mqin, int mqmax) {
		this.centralServer = centralServer;
		nodesinfo = centralServer.getNodesInfo();
		this.mqin = mqin;
		this.mqmax = mqmax;
		
		new Thread(this, "RouteMessage").start();
	}
	
	
	public void run() {
		
		//Initialize sending connections with nodes
		mqoutarr = new ArrayList< ArrayBlockingQueue<String> >(4);
		
		for (int i=0; i<4; i++) {
			mqoutarr.add(new ArrayBlockingQueue<String>(mqmax));
			Socket servconn = null;
			while (servconn == null) {
				try {
					servconn = new Socket(nodesinfo[i].ip, nodesinfo[i].port);
    				System.out.print("Now connected to "+nodesinfo[i].ip+":"+nodesinfo[i].port);
    				System.out.println(" (node "+nodesinfo[i].id+")");
    				centralServer.setSendingThreadIndex(i,
    						new MessageSenderThread(centralServer, mqoutarr.get(i), i, servconn));
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
	        		mqoutarr.get(recvIdx).put(msg);
	        	}
				
			}
			addMessageToAllQueues(msg); //exit message
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	
	public void addMessageToAllQueues(String msg) {
		try {
        	for (Iterator< ArrayBlockingQueue<String> > it = mqoutarr.iterator(); it.hasNext();) {
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
			int i = 3; //starting index into message
			
			while (msg.charAt(i) == ' ') //move past spaces between get,key
				i++;
			while (msg.charAt(i) != ' ') //move past key
				i++;
			while (msg.charAt(i) == ' ') //move past spaces between key,model
				i++;
			while (msg.charAt(i) != ' ') //move past model
				i++;
			//msg.charAt(i) is now the space between model and other data
		}
		
		//send message destination
		else if (msg.substring(0, 5).compareToIgnoreCase("send ") == 0) {
			
		}
		
		//delete key <requestingnodeid> <reqorack> <timestamp>
		else if (msg.substring(0, 7).compareToIgnoreCase("delete ") == 0) {
			
		}
		
		//insert key value model <requestingnodeid> <requestnumber> <value> <reqorack> <timestamp>
		else if (msg.substring(0, 7).compareToIgnoreCase("insert ") == 0) {
			
		}
		
		//update key value model <requestingnodeid> <requestnumber> <value> <reqorack> <timestamp>
		else if (msg.substring(0, 7).compareToIgnoreCase("update ") == 0) {
			
		}
		
		return ret;
	}

}
