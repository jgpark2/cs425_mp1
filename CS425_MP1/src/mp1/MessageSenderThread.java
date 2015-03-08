package mp1;

import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * MessageSenderThread: 4 per CentralLeader object
 * Takes input from the blocking queue,
 * and sends that request over the socket associated with this
 */
public class MessageSenderThread extends Thread {

	private CentralServer centralServer;
	private NodeInfo [] nodesinfo;
	private int myIdx;
	
	private Socket socket;
	private ArrayBlockingQueue<String> mqout;

	public MessageSenderThread(CentralServer centralServer,
			ArrayBlockingQueue<String> mqout, int i,
			Socket servconn) {
		
		this.centralServer = centralServer;
		this.nodesinfo = centralServer.getNodesInfo();
		this.mqout = mqout;
		myIdx = i;
		socket = servconn;
	}

}
