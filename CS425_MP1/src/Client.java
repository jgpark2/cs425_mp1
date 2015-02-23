import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Client
{
	public static void main(String[] args) throws Exception
	{
		String serverAddr = "127.0.0.1";
		Socket client = new Socket(serverAddr, 3490);
		BufferedReader input = new BufferedReader(new InputStreamReader(client.getInputStream()));
		String answer = input.readLine();
		System.out.println(answer);
		client.close();
	}
}
