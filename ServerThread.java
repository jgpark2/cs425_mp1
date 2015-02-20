import java.net.ServerSocket;
import java.net.Socket;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ServerThread
{
	//run the server thread
	//have public methods such as setting up on a certain port/ip
	//it will call methods in ServerThreads to convey messages
	//
	/*
	 *		ServerSocket s = new ServerSocket(3490); //s for server
		Socket c = new Socket(ip, 3490); //c for client
		int count = 0;
		try {
			while (count < 4) {
				c = s.accept();
				try {
					PrintWriter out = new PrintWriter(c.getOutputStream(), true);
					out.println("Hello World!\n");
					count++;
				}
				finally {
					c.close();
				}
			}
		}
		finally {
			s.close();
		}
	 */
}
