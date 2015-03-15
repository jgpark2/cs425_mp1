package mp1;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * MessageSenderThread: 1 per Node object
 * Takes input from the blocking queue,
 * and sends that request to the CentralServer
 * Since the CentralServer is not the final destination of the message,
 * no delay is applied until it will reach its final destination
 */
public class MessageSenderThread extends Thread {

	private int myIdx; //index into NodeInfo array for this Node
	
	//Socket connection
	private Socket socket;
	private PrintWriter outs;
	
	//Queue to take messages from and send
	private ArrayBlockingQueue<String> mqout;
	
	
	public MessageSenderThread(Node node, ArrayBlockingQueue<String> mq,
			Socket socket) {
		
		myIdx = node.myIdx;
		this.socket = socket;
		mqout = mq;
		
		new Thread(this, "SenderToLeader").start();
	}


	/*
	 * This thread's main purpose is to wait for any message to arrive on the
	 * FIFO channel queue (inserted into either by CommandInputThread),
	 * then deliver it.
	 */
	public void run() {
		
		//Initialization
		try {
			outs = new PrintWriter(socket.getOutputStream(), true);
		} catch (IOException e) {
			System.out.println("Unable to open socket stream");
			e.printStackTrace();
			try {
				socket.close();
				outs.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
		}
		
		//send myIdx to the MessageRelayThread on the other end (at the CentralServer)
		outs.println(myIdx);
		
		while (true) {
			String msg = "";
			try {
				msg = mqout.take();
				//while MessageRelayThread keeps reading from its input stream...
	        	outs.println(msg);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}

}
