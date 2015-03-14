package mp1;

public class MessageType {
	public String msg = "";
	public Long ts = new Long(0); //time to send at
	
	public MessageType() {
		msg = "";
		ts = new Long(0);
	}
	
	public MessageType(String msg, Long time) {
		this.msg = new String(msg);
		this.ts = time;
	}
	
	public MessageType(MessageType msg) {
		this.msg = new String(msg.msg);
		this.ts = msg.ts;
	}
	
	//TODO: parse any message received over a socket for its timestamp
	//timestamp should always be at the end of the message
	public static long getTimeStampOutOfMessage(String msg) {
		return 0;
	}
}
