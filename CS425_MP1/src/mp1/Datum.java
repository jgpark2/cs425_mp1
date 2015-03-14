package mp1;

public class Datum {

	public String value;
	public long timestamp;
	public long timestamp2; //maybe
	
	public Datum(String val, long ts) {
		value = new String(val);
		timestamp = ts;
	}
	
	public Datum(String val, long ts, long ts2) {
		value = new String(val);
		timestamp = ts;
		timestamp2 = ts2;
	}
	
	public void addAckTimestamp(long ts2) {
		timestamp2 = ts2;
	}
}
