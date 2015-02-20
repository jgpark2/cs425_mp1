import java.net.ServerSocket;
import java.net.Socket;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;

public class Node
{
	private String id; //from user
	private String ip; //filled in by config file
	private int port;
	
	private int connect()
	{
		return -1;
	}
	public static void main(String[] args) throws Exception
	{
		if (args.length != 2) {
			System.out.println("usage: java Server [server_id]");
			return;
		}
		id = args[1].toUpperCase();
		if (id.compareTo("A") != 0
			&& id.compareTo("B") != 0
			&& id.compareTo("C") != 0
			&& id.compareTo("D") != 0) {
			System.out.println("server ids must be A, B, C, D");
			return;
		}

		//Parsing config file
		File configfile = new File("config");
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(configfile);
			
		} catch (Exception e) { //if config file can't be parsed, exit
			e.printStackTrace();
			return;
		} finally {
			try {
				if (fis != null) fis.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}
