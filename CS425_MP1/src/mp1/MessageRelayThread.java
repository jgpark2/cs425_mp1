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
	
	private Socket socket;
	private BufferedReader ins;
	private ArrayBlockingQueue<String> mqin;
	
	
	public MessageRelayThread(CentralServer centralServer, Socket socket,
			BufferedReader ins, ArrayBlockingQueue<String> mqin) {
		this.centralServer = centralServer;
		nodesinfo = centralServer.getNodesInfo();
		this.socket = socket;
		this.ins = ins;
		this.mqin = mqin;
		
		new Thread(this, "RelayMessage").start();
	}


	public void run() {
		
		try {
			//while Node keeps writing to its output stream...
			String input = "";
			
			while (((input = ins.readLine()) != null) && (input.compareToIgnoreCase("exit") != 0))
				mqin.put(input);
				
			mqin.put("exit");
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
	}

}
