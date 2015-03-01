package mp1;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Node {

	private NodeInfo[] nodesinfo;
	private NodeInfo myInfo;
	private NodeThreads threads;
	private ServerThread st;
	//private ClientThread ct;
	private MessageThread mt;
	
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1) {
			System.out.println("Usage: java Node [node_id]");
			System.exit(1);
			return;
		}
		
		Node node = new Node();
		
		if (node.parse(args) == -1) {
			System.out.println("Failed to parse config");
			System.exit(1);
			return;
		}
		
		node.start();
		
		
	}
	
	
	private int parse(String[] args)
	{
		nodesinfo = new NodeInfo[4];
		
		
		for (int i=0; i<4; i++)
			nodesinfo[i] = new NodeInfo();
		
		String id = args[0].toUpperCase();
		
		if (id.compareTo("A") != 0
			&& id.compareTo("B") != 0
			&& id.compareTo("C") != 0
			&& id.compareTo("D") != 0) {
			System.out.println("node ids must be A, B, C, or D");
			return -1;
		}

		//Parsing config file
		//File configfile = new File("/home/galbrth2/cs425/cs425_mp1/CS425_MP1/src/config"); //can't seem to make this a non-absolute path
		File configfile = new File("config");
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(configfile);
			StringWriter str = new StringWriter();
			int content;
			
			for (int i=0; i<4; i++) { //four nodes id, ip
				content = fis.read(); //node id
				str.write(content);
				nodesinfo[i].id = str.toString();
				
				if (str.toString().compareTo(id)==0) {
					System.out.println("Identified this node as "+str.toString());
					myInfo = nodesinfo[i];
				}
				
				
				str = new StringWriter();
				
				content = fis.read(); //,
				
				while ((char)(content = fis.read()) != '\n') //node ip
					str.write(content);

				nodesinfo[i].ip = str.toString();
				str = new StringWriter();
			}
			
			for (int i=0; i<4; i++) { //four nodes port
				content = fis.read(); //node id
				
				content = fis.read(); //,
				
				while ((char)(content = fis.read()) != '\n') //node port
					str.write(content);

				Integer portnumint = new Integer(str.toString());
				str = new StringWriter();
				nodesinfo[i].port = portnumint.intValue();
			}
			
			for (int i=0; i<4; i++) { //four nodes max delay
				content = fis.read(); //node id

				content = fis.read(); //,

				while ((char)(content = fis.read()) != '\n') //node max delay
					str.write(content);
				
				Double mddouble = new Double(str.toString());
				str = new StringWriter();
				nodesinfo[i].max_delay = mddouble.doubleValue();
			}
			
		} catch (Exception e) { //if config file can't be parsed, exit
			e.printStackTrace();
			return -1;
		} finally {
			try {
				if (fis != null) fis.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		for (int i=0; i<4; i++) { //test that node info is correct
			System.out.print("Node " + nodesinfo[i].id + ": ");
			System.out.print("IP: " + nodesinfo[i].ip + " ");
			System.out.print("Port: " + nodesinfo[i].port + " ");
			System.out.println("Max delay: " + nodesinfo[i].max_delay);
		}
		return 0;
	}
	
	public void start() {                
        //Start the Client thread which will eventually spawn 3 RECV threads
		new Thread(new Client(nodesinfo, myInfo), "Receiver").start();
        
		
    	int serverPort = myInfo.port;
    	ServerSocket serverSocket;
		//reference: http://docs.oracle.com/javase/tutorial/networking/sockets/clientServer.html
		//http://stackoverflow.com/questions/10131377/socket-programming-multiple-client-to-one-server
    	System.out.println("Now listening on "+serverPort+"...");
    	
        try {
        	serverSocket = new ServerSocket(serverPort);
        } catch (IOException e) {
			System.out.println("Could not listen on port "+serverPort);
			System.exit(-1);
			return;
        }
        
        //Start the SEND thread for each connection
        //We should keep a list of these sockets...
        Socket socket;
        
        while(true){
            try{
                socket = serverSocket.accept();
                System.out.println("Connection established with a client.");
                ServerThread st=new ServerThread(nodesinfo, myInfo, socket);
                st.start();

            } catch(Exception e){
            	e.printStackTrace();
            	System.out.println("Connection accept failed: "+serverPort);
            }
        }
        //new Thread(new ServerThread(nodesinfo, myInfo), "Sender").start();
    	
        
		//threads = new NodeThreads(nodesinfo);
	}
}
