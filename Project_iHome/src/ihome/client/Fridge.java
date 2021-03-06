package ihome.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.SaslSocketServer;
import org.apache.avro.ipc.SaslSocketTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.avro.ipc.specific.SpecificResponder;
import org.apache.avro.ipc.Server;
import org.json.JSONException;
import org.json.JSONObject;

import ihome.proto.sensorside.SensorProto;
import ihome.proto.serverside.ServerProto;
import ihome.proto.userside.UserProto;
import ihome.server.Controller;
import ihome.server.Device;
import ihome.proto.fridgeside.FridgeProto;
import ihome.proto.lightside.LightProto;

public class Fridge implements FridgeProto {
	
	final static int wtna = Controller.check_alive_interval / 3; 
	
	private Server server = null;
	private Controller controller;
	private Transceiver fridge;
	private ServerProto proxy;

	// Fridge variables
	private String name;
	private int ID;
	private String IPAddress;
	private String server_ip_address;
	private boolean opened = false;
	private ArrayList<CharSequence> items = new ArrayList<CharSequence>();
	private ArrayList<CharSequence> allItems = new ArrayList<CharSequence>();
	
	// Alive caller variables
	private AliveCaller ac;
	private Timer timer;
	
	// Leader election variables
	private Boolean participant = false;
	private Boolean isLeader = false;
	private int elected = -1;
	
	
	
	/******************
	 ** CONSTRUCTORS **
	 ******************/
	public Fridge() {}
	public Fridge(String ip_address, String server_ip) {
		IPAddress = ip_address;
		server_ip_address = server_ip;
		controller = new Controller(ip_address, false);
	}
	
	
	/**************************
	 ** SERVER FUNCTIONALITY **
	 **************************/
	public void connect_to_server() {
		try {
			try {
				fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6789));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
			} catch(Exception e) {
				fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, 6788));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
				elected = -2;
			}
			
			CharSequence response = proxy.connect(2, IPAddress);
			JSONObject json = new JSONObject(response.toString());
			if (!json.isNull("Error")) throw new Exception();
			
			ID = json.getInt("UID");
			name = "fridge" + ID;
			System.out.println("Connected to server with name " + name + " and ID: " + ID);
			
			// Start timer for I'm alive
			timer = new Timer();
			ac = new AliveCaller(this);
			timer.scheduleAtFixedRate(ac, wtna, wtna);
		} catch (Exception e) {
			System.err.println("[Error] Failed to connect to server");
			System.exit(1);
		}
	}
	
	public void runServer() {
		try
		{
			server = new SaslSocketServer(new SpecificResponder(FridgeProto.class,
					this), new InetSocketAddress(IPAddress, 6790+ID));
		}catch (IOException e){
			System.err.println("[Error] Failed to start server");
			System.exit(1);

		}
		server.start();
	}
	
	public void stopServer() {
		try {
			server.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void pullServer() {
		try {
			proxy.sendController();
		} catch (AvroRemoteException e) {
			System.err.println("[Error] Failed to pull from server");
		}
	}
	
	
	/*************************
	 ** ALIVE FUNCTIONALITY **
	 *************************/
	public void send_alive(){
		try {
			proxy.i_am_alive(this.ID);
		} catch (AvroRemoteException e) {
			if (!this.participant) {
				this.startElection();
			}
		}
	}
	
	
	/**************
	 ** ELECTION **
	 **************/
	public boolean sendElection(int nextID, CharSequence ipaddress, int receivedID) {
		if(nextID == elected){
			return false;
		}
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.controller.getUidmap().get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.Callback.class, cand);
				uproxy.receiveElection(receivedID);
			} else if (this.controller.getUidmap().get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.Callback.class, cand);
				sproxy.receiveElection(receivedID);
			} else if (this.controller.getUidmap().get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.Callback.class, cand);
				fproxy.receiveElection(receivedID);
			} else if (this.controller.getUidmap().get(nextID).type == 3){
				LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.Callback.class, cand);
				lproxy.receiveElection(receivedID);
			}
			cand.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public boolean sendElected(int nextID, CharSequence ipaddress, CharSequence serverIP, int port, int sid) {
		if(nextID == this.elected){
			return false;
		}
		try {
			Transceiver cand = new SaslSocketTransceiver(new InetSocketAddress(ipaddress.toString(), 6790 + nextID));
			if(this.controller.getUidmap().get(nextID).type == 0){
				UserProto uproxy = (UserProto) SpecificRequestor.getClient(UserProto.Callback.class, cand);
				uproxy.receiveElected(serverIP, port, sid);
			} else if (this.controller.getUidmap().get(nextID).type == 1){
				SensorProto sproxy = (SensorProto) SpecificRequestor.getClient(SensorProto.Callback.class, cand);
				sproxy.receiveElected(serverIP, port, sid);
			} else if (this.controller.getUidmap().get(nextID).type == 2){
				FridgeProto fproxy = (FridgeProto) SpecificRequestor.getClient(FridgeProto.Callback.class, cand);
				fproxy.receiveElected(serverIP, port, sid);
			} else if (this.controller.getUidmap().get(nextID).type == 3){
				LightProto lproxy = (LightProto) SpecificRequestor.getClient(LightProto.Callback.class, cand);
				lproxy.receiveElected(serverIP, port, sid);
			}
			cand.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public CharSequence startElection() {
		this.participant = true;
		
		int nextID = this.controller.getNextID(this.ID);
		if (nextID != this.ID) {
			CharSequence nextIP = this.controller.getIP(nextID);
			
			while (!this.sendElection(nextID, nextIP, this.ID)) {
				nextID = this.controller.getNextID(nextID);
				if (nextID == this.ID) {
					// Start my server
					this.controller.runServer();
					try {
						fridge = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6788));
						proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
						this.elected = this.ID;
						this.server_ip_address = this.IPAddress;
						System.out.println("\nA new controller has been selected with IP address " + this.server_ip_address);
					} catch (IOException e) {
						System.err.println("[Error] Failed to start server");
					}
					this.participant = false;
					return null;
				}
				nextIP = this.controller.getIP(nextID);
			}
		} else {
			// Start my server
			this.controller.runServer();
			try {
				fridge = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6788));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);
				this.elected = this.ID;
				this.server_ip_address = this.IPAddress;
				System.out.println("\nA new controller has been selected with IP address " + this.server_ip_address);
			} catch (IOException e) {
				System.err.println("[Error] Failed to start server");
			}
			this.participant = false;
		}
		return null;
	}
	
	@Override
	public CharSequence receiveElection(int receivedID) throws AvroRemoteException {
		if (receivedID > this.ID) {
			// Forward the election message
			this.participant = true;
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElection(nextID, nextIP, receivedID)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
			}
		} else if (receivedID < this.ID && !this.participant) {
			// Send my ID
			this.participant = true;
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElection(nextID, nextIP, this.ID)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
			}
		} else if (receivedID < this.ID && this.participant) {
			// Discard election message
		} else if (receivedID == this.ID) {
			// I'm the leader
			this.participant = false;
			// Start my server
			this.controller.runServer();
			try {
				fridge = new SaslSocketTransceiver(new InetSocketAddress(IPAddress, 6788));
				proxy = (ServerProto) SpecificRequestor.getClient(ServerProto.class, fridge);	
				this.elected = this.ID;
				this.server_ip_address = this.IPAddress;
				System.out.println("\nA new controller has been selected with IP address " + this.server_ip_address);
			} catch (IOException e) {
				System.err.println("[Error] Failed to start server");
			}
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElected(nextID, nextIP, this.IPAddress, 6788, this.ID)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
			}
		}
		return " ";
	}
	
	@Override
	public CharSequence receiveElected(CharSequence serverIP, int port, int serverID) throws AvroRemoteException {
		if (this.participant) {
			this.participant = false;
			this.server_ip_address = serverIP.toString();
			try {
				fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
				proxy = SpecificRequestor.getClient(ServerProto.Callback.class, fridge);
				this.elected = serverID;
				System.out.println("A new controller has been selected with IP address " + this.server_ip_address);
			} catch (IOException e) {
				System.err.println("[Error] Failed to start server");
			}
			// Forward elected message
			int nextID = this.controller.getNextID(this.ID);
			CharSequence nextIP = this.controller.getIP(nextID);
			while (!this.sendElected(nextID, nextIP, serverIP, port, serverID)) {
				nextID = this.controller.getNextID(nextID);
				nextIP = this.controller.getIP(nextID);
			}
		} else {
			// Discard. Election is over.
			System.out.println("A new controller has been selected with IP address " + this.server_ip_address);
		}
		return " ";
	}
	
	@Override
	public int ReceiveCoord(CharSequence server_ip, int port) throws AvroRemoteException {
		this.server_ip_address = server_ip.toString();
		try {
			fridge = new SaslSocketTransceiver(new InetSocketAddress(server_ip_address, port));
			proxy = SpecificRequestor.getClient(ServerProto.Callback.class, fridge);
			this.elected = -1;
			System.out.println("A new controller has been selected with IP address " + this.server_ip_address);
		} catch (IOException e) {
			System.err.println("[Error] Failed to start server");
		}
		this.participant = false;
		this.isLeader = false;
		return 0;
	}
	
	@Override
	public CharSequence getLeader() throws AvroRemoteException {
		try {
			JSONObject json = new JSONObject();
			json.put("lastServerID", elected);
			return json.toString();
		} catch (JSONException e) {
			return "";
		}
	}
	
	public void askLeaderID() {
		CharSequence response = "";
		Map<Integer, Device> uidmap = this.controller.getUidmap();
		for (int id : uidmap.keySet()) {
			try {
				if (uidmap.get(id).type == 0 && uidmap.get(id).is_online) {
					Transceiver user = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					UserProto userproxy = SpecificRequestor.getClient(UserProto.class, user);
					response = userproxy.getLeader();
					user.close();
				} else if (uidmap.get(id).type == 2 && uidmap.get(id).is_online) {
					// Send me to fridge
					Transceiver fridge = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					FridgeProto fridgeproxy = SpecificRequestor.getClient(FridgeProto.class, fridge);
					response = fridgeproxy.getLeader();
					fridge.close();
				} else if (uidmap.get(id).type == 1 && uidmap.get(id).is_online) {
					// Send uidmap to sensor
					Transceiver sensor = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					SensorProto sensorproxy = SpecificRequestor.getClient(SensorProto.class, sensor);
					response = sensorproxy.getLeader();
					sensor.close();
				} else if (uidmap.get(id).type == 3 && uidmap.get(id).is_online) {
					// Send uidmap to light
					Transceiver light = new SaslSocketTransceiver(new InetSocketAddress(6790+id));
					LightProto lightproxy = SpecificRequestor.getClient(LightProto.class, light);
					response = lightproxy.getLeader();
					light.close();
				}
			} catch (Exception e) {
				continue;
			}
			if (response != "") break;
		}
		// Unpack response
		if (response != "") {
			JSONObject json;
			try {
				json = new JSONObject(response.toString());
				elected = json.getInt("lastServerID");
			} catch (JSONException e) {
				System.err.println("[Error] JSON exception");
			}
		}
	}
	
	/**************************
	 ** FRIDGE FUNCTIONALITY **
	 **************************/
	public void open() {
		opened = true;
	}
	
	public void close() {
		opened = false;
	}
	
	
	/**************************
	 ** ITEMS FUNCTIONALITY  **
	 **************************/
	public void print_items() {
		for (CharSequence item : items) {
			System.out.println(item);
		}
	}
	
	public ArrayList<CharSequence> get_items() {
		return items;
	}
	
	@Override
	public CharSequence add_item(CharSequence item) throws AvroRemoteException {
		if (items.contains(item)) {
			item = item +  "0";
		}
		items.add(item);
		return "";
	}

	@Override
	public CharSequence remove_item(CharSequence item) throws AvroRemoteException {
		this.items.remove(item);
		if(this.items.isEmpty())
		proxy.notify_empty_fridge(this.ID);
		return "";
	}
	
	@Override
	public CharSequence send_current_items() throws AvroRemoteException {
		return Arrays.toString(items.toArray());
	}

	@Override
	public CharSequence send_all_items() throws AvroRemoteException {
		return Arrays.toString(allItems.toArray());
	}
	
	
	/******************************
	 ** CONTROLLER FUNCTIONALITY **
	 ******************************/
	
	@Override
	public CharSequence update_controller(CharSequence jsonController) throws AvroRemoteException {
		if (this.elected != this.ID) {
			return controller.updateController(jsonController);
		}
		return " ";
	}
	
	
	/**************************
	 ** MAIN FUNCTIONALITY   **
	 **************************/
	public static void main(String[] args) {
		Scanner reader = new Scanner(System.in);
		System.out.println("What is your IP address?");
		String ip_address = reader.nextLine();
		System.out.println("What is the servers IP address?");
		String server_ip = reader.nextLine();
		Fridge myFridge = new Fridge(ip_address, server_ip);
		
		myFridge.connect_to_server();
		myFridge.runServer();
		myFridge.pullServer();
		if (myFridge.elected == -2) {
			// Ask leader ID
			myFridge.askLeaderID();
		}
		
		reader.close();
		while (true) {
			continue;
		}
	}
}
