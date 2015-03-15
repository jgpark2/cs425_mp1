package mp1;

import java.io.BufferedReader;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * MessageRelayThread: 4 per CentralServer object
 * Receives messages from the nodes and puts them in the
 * totally-ordered queue to be sorted out by MessageRouterThread
 */
public class MessageRelayThread extends Thread {
	
	//Structure to hold information from config file
	private NodeInfo [] nodesinfo;
	//Node id of the node that this thread is receiving messages from
	private String recvId;
	
	//Stream from socket connection
	private BufferedReader ins;
	
	//Totally-ordered queue to add messages to
	private ArrayBlockingQueue<String> mqin;
	
	
	public MessageRelayThread(CentralServer centralServer, Socket socket,
			BufferedReader ins, ArrayBlockingQueue<String> mqin, int recvIdx) {
		
		nodesinfo = centralServer.getNodesInfo();
		this.recvId = nodesinfo[recvIdx].id;
		this.ins = ins;
		this.mqin = mqin;
		
		new Thread(this, "RelayMessage").start();
	}


	/*
	 * This thread's main purpose is to wait until a message is received
	 * over the socket connection, then add it to the totally-ordered queue
	 */
	public void run() {
		
		try {
			//while MessageSenderThread keeps writing to its output stream...
			String input = "";
			while ((input = ins.readLine()) != null)
				mqin.put(input+" "+recvId); //the Node that sent the message is appended
				
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
	}

}
