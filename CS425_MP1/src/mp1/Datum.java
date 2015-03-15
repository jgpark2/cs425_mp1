package mp1;

/*
 * This simple class represents the value in our key-value store
 */
public class Datum {

	//The value that was assigned in a write operation
	public String value;
	
	//The timestamp of the invocation of the operation that
	//the value was assigned in
	public long timestamp;

	
	public Datum(String val, long ts) {
		value = new String(val);
		timestamp = ts;
	}

}
