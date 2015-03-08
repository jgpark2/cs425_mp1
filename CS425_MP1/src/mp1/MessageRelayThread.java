package mp1;

import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * MessageRelayThread: 4 per CentralServer object
 * Receives messages from the nodes and puts them in the
 * totally-ordered queue to be sorted out by MessageRouterThread
 */
public class MessageRelayThread extends Thread {
	
	private CentralServer centralServer;
	
	private Socket socket;
	private ArrayBlockingQueue<MessageType> mqin;

	public MessageRelayThread(CentralServer centralServer, Socket socket,
			ArrayBlockingQueue<MessageType> mqin) {
		this.centralServer = centralServer;
		this.socket = socket;
		this.mqin = mqin;
		
		new Thread(this, "RelayMessage").start();
	}

}
