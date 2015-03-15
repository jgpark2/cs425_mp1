package mp1;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * MessageDelayerThread: 3 per Node object
 * Takes input from the blocking queue, sleeps for the desired amount,
 * and sends that request over the socket associated with this
 */
public class MessageDelayerThread extends Thread {

	private int myIdx; //index into NodeInfo array
	
	private ArrayBlockingQueue<MessageType> mq;
	
	private Socket socket;
	private PrintWriter outs;
	

    public MessageDelayerThread(NodeInfo [] nodesinfo, int myIdx, ArrayBlockingQueue<MessageType> mq,
    		String recvId, Socket connection) {

    	this.myIdx = myIdx;
    	this.mq = mq;
		socket = connection;
		
		new Thread(this, "DelayInput"+recvId).start();
	}

	public void run() {
		
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
		
		//send myIdx to the MessageReceiverThread on the other end
		outs.println(myIdx);
    	

		while(true) {
			MessageType msg = new MessageType();
			try {
				msg = mq.take();
				long tosleep = msg.ts - System.currentTimeMillis();
				if (tosleep > 0) {
					Thread.sleep(tosleep);
				}
				
				//while MessageReceiverThread keeps reading from its input stream...
				outs.println(msg.msg);
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
    }
 
}
