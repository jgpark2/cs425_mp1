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
	
	private CentralServer centralServer;
	private NodeInfo [] nodesinfo;
	private String recvId;
	
	private Socket socket;
	private BufferedReader ins; //coming from socket connection
	private ArrayBlockingQueue<String> mqin;
	
	
	public MessageRelayThread(CentralServer centralServer, Socket socket,
			BufferedReader ins, ArrayBlockingQueue<String> mqin, int recvIdx) {
		this.centralServer = centralServer;
		nodesinfo = centralServer.getNodesInfo();
		this.recvId = nodesinfo[recvIdx].id;
		this.socket = socket;
		this.ins = ins;
		this.mqin = mqin;
		
		new Thread(this, "RelayMessage").start();
	}


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
