package mp1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;

/*
 * MessageRelayThread: 4 per CentralServer object
 * Receives messages from the nodes and puts them in the
 * totally-ordered queue to be sorted out by MessageRouterThread
 */
public class MessageRelayThread extends Thread {
	
	private CentralServer centralServer;
	private NodeInfo [] nodesinfo;
	private int recvIdx; //index into NodeInfo array
	
	private Socket socket;
	private BufferedReader ins;
	private ArrayBlockingQueue<String> mqin;

	
	public MessageRelayThread(CentralServer centralServer, Socket socket,
			ArrayBlockingQueue<String> mqin) {
		this.centralServer = centralServer;
		nodesinfo = centralServer.getNodesInfo();
		this.socket = socket;
		this.mqin = mqin;
		
		new Thread(this, "RelayMessage").start();
	}
	
	
	public void run() {
		
		try {
			ins = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		} catch (IOException e) {
			System.out.println("Could not initialize socket stream");
			e.printStackTrace();
			try {
				socket.close();
				ins.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
		}
		
		String input = "";
		//exchange recvInfo with socket
		try {
			while ((input = ins.readLine())==null) {}
			recvIdx = Integer.parseInt(input);
			centralServer.setReceivingThreadIndex(recvIdx, this);
		} catch (IOException e1) {
			System.out.println("Could not receive initial info exchange");
			e1.printStackTrace();
			try {
				socket.close();
				ins.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}
		
		try {
			//while Node keeps writing to its output stream...
			while (((input = ins.readLine()) != null) && (input.compareToIgnoreCase("exit") != 0)) {
				mqin.put(input);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
	}

}
