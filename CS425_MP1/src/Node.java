import java.net.ServerSocket;
import java.net.Socket;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;

public class Node {
	
	private NodeInfo[] nodesinfo = new NodeInfo[4];
	
	private int parse(String[] args)
	{
		if (args.length != 2) {
			System.out.println("usage: java Server [server_id]");
			return -1;
		}
		
		id = args[1].toUpperCase();
		
		if (id.compareTo("A") != 0
			&& id.compareTo("B") != 0
			&& id.compareTo("C") != 0
			&& id.compareTo("D") != 0) {
			System.out.println("server ids must be A, B, C, D");
			return -1;
		}

		//Parsing config file
		File configfile = new File("config");
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(configfile);
			
			int content;
			while ((content = fis.read()) != -1) {
				if ((char)content == id.charAt(0)) {
					if (ip == "") { //read in rest of IP
						ip.
						while ((char)(content = fis.read()) != '\n') {
							
						}
					}
				}
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
		return 0;
	}
	
	public static void main(String[] args) throws Exception
	{
		Node node = new Node();
		if (node.parse(args) == -1)
			return;
	}

}
