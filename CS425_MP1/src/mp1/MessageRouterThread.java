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

	private ArrayBlockingQueue<MessageType> mqin;
	private ArrayList< ArrayBlockingQueue<MessageType> > mqoutarr;
	private int mqmax;
	

	public MessageRouterThread(CentralServer centralServer,
			ArrayBlockingQueue<MessageType> mqin, int mqmax) {
		this.centralServer = centralServer;
		nodesinfo = centralServer.getNodesInfo();
		this.mqin = mqin;
		this.mqmax = mqmax;
		
		new Thread(this, "CreateConnections").start();
	}
	
	
	public void run() {
		
		//Initialize sending connections with nodes
		mqoutarr = new ArrayList< ArrayBlockingQueue<MessageType> >(4);
		
		for (int i=0; i<4; i++) {
			mqoutarr.add(new ArrayBlockingQueue<MessageType>(mqmax));
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
			MessageType msg;
			while ((msg = mqin.take()).msg.compareToIgnoreCase("exit") != 0) {
				
			}
			addMessageToAllQueues(msg);
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

}
