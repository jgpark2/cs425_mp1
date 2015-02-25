import java.net.*;
import java.io.*;
import java.util.*;

/*
 * This class should be on a thread.
 * blahblahblah
 */
public class Client
{
	public static void main(String[] args) throws Exception
	{
		String serverAddr = "127.0.0.1";
		Integer serverPort = 7500;
		
		Socket client;
		PrintWriter out;
		BufferedReader in;
		//connect to server of given addr and port
		try {
			client = new Socket(serverAddr, serverPort);
			out = new PrintWriter(client.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(client.getInputStream()));
		} catch (IOException e) {
			System.out.println("Connect, in, out failed");
			System.exit(-1);
			return;
		}
		
		String msgInput, msgOutput;
		Scanner sc = new Scanner(System.in);
		
		//While the server keeps writing to us on its own output Stream...
		while((msgInput = in.readLine())!=null) {
			System.out.println("Server: " + msgInput);
			
			if (msgInput.equals("Bye")) //Recevied disconnect Msg
				break;
			
			msgOutput = sc.next();
			System.out.println("Client: " + msgOutput);
			out.println(msgOutput);
		}
		
		client.close();
	
	}
}
