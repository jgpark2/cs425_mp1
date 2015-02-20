import java.net.ServerSocket;
import java.net.Socket;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Server
{
	private static String ip = "127.0.0.1"; //localhost
	
	private int connect()
	{

	}
	public static void main(String[] args) throws Exception
	{
		if (args.size() != 2) {
			System.out.println("usage: java Server [server_label]");
			return;
		}
		String label = args[1];
		if (label.compareToIgnoreCase("a") != 0
			&& label.compareToIgnoreCase("b") != 0) {
			System.out.println("server labels must be A or B");
			return;
		}

		ServerSocket s = new ServerSocket(3490); //s for server
		Socket c = new Socket(ip, 3490); //c for client
		int count = 0;
		try {
			while (count < 4) {
				Socket client = listener.accept();
				try {
					PrintWriter out = new PrintWriter(client.getOutputStream(), true);
					out.println("Hello World!\n");
					count++;
				}
				finally {
					client.close();
				}
			}
		}
		finally {
			listener.close();
		}
	}
}
