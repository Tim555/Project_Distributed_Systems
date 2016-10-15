package ihome.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.Server;
import org.apache.avro.ipc.specific.SpecificResponder;

import ihome.proto.serverside.ServerProto;


public class Controller implements ServerProto 
{
	
	private Map<String, Device> uidmap = new HashMap<String, Device>();
	private Map<String, ArrayList<Float>> sensormap = new HashMap<String, ArrayList<Float>>();
	private int nextID = 0;
	private final int nr_types = 4;

	@Override
	public CharSequence connect(int device_type) throws AvroRemoteException {
		
		if(device_type < 0 || device_type >= nr_types)
		{
			return "{\"UID\" : NULL, \"Error\" : \"[Error] No such type defined.\"}";
		}
		
		try{
			uidmap.put(Integer.toString(nextID), new Device(device_type));
			if(device_type == 1)
				sensormap.put(Integer.toString(nextID), new ArrayList<Float>());
			return "{\"UID\" : \""+ (nextID++) + "\", \"Error\" : NULL}";
		}catch(Exception e){
			return "{\"UID\" : NULL, \"Error\" : \"[Error] " + e.getMessage();
		}
	}

	@Override
	public CharSequence disconnect(CharSequence uid) throws AvroRemoteException {
		
		if(!uidmap.containsKey(uid))
		{
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		try{
			uidmap.remove(uid);
			return "{\"Error\" : NULL}";
		}catch(Exception e){
			return "{\"Error\" : \"[Error] " + e.getMessage();
		}
		
	}
	
	@Override
	public CharSequence update_temperature(CharSequence uid, float value) throws AvroRemoteException {
		if(!uidmap.containsKey(uid)) {
			return "{\"Error\" : \"[Error] uid not found in current session.\"}";
		}
		else if(!sensormap.containsKey(uid)){
			return "{\"Error\" : \"[Error] Device with uid " + uid +  " is not heat sensor.\"}";
		}
		try{

		}catch (Exception e){

		}
		return null;
	}

	

	@Override
	public CharSequence get_temperature_list(CharSequence uid, CharSequence sensor_id) throws AvroRemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CharSequence get_temperature_current(CharSequence uid) throws AvroRemoteException {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	public void printInSession(){
		System.out.println("Currently in session:");
		for(String id : uidmap.keySet())
		{
			System.out.print(id + " ");
		}
		System.out.print("\n");
	}

	public static void runServer(){
		Server server = null;
		try
		{
			server = new SaslSocketServer(new SpecificResponder(ServerProto.class,
					new Controller()), new InetSocketAddress(6789));
		}catch (IOException e){
			System.err.println("[error] failed to start server");
			e.printStackTrace(System.err);
			System.exit(1);

		}
		server.start();
		try{
			server.join();

		}catch(InterruptedException e){}
	}
	
	public static void main(String [] args){
		Controller.runServer();
	}
}
