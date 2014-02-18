package se.sandos.android;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class MainActivity extends Activity {
	private static final int PKT_HELLO = 123456;
	private static final int PKT_PING  = 987655;
	private static final int PKT_PONG  = 345678;
	
	private static final String TAG = "majs";
	private static final int UDP_PORT = 49152;
	private static final int UDP_MAX_SIZE = 1000;
	
	private DatagramSocket serverSocketUDP;
	private int pingCounter = 0;
	private MulticastLock ml;
	
	private InetAddress ip;
	private InetAddress broadcast;
	
	private Thread sendThread;
	private final BlockingQueue<DatagramPacket> sendQueue = new ArrayBlockingQueue<DatagramPacket>(10);
	
	private Thread paceThread;
	
	private DatagramSocket sendSocket;
	private DatagramPacket helloWorldPacket;
	final private BinaryMessage binaryMessage = new BinaryMessage();
	private DatagramPacket dataPacket;
	
	final private PeerPair pairTemp = new PeerPair();
	
	private ConcurrentHashMap<PeerPair, Latency> latencies = new ConcurrentHashMap<PeerPair, Latency>(10, 0.5f, 2);
	
	private ConcurrentHashMap<InetAddress, Boolean> peersBacking = new ConcurrentHashMap<InetAddress, Boolean>(10, 0.5f, 2);
	private Set<InetAddress> peers = Collections.newSetFromMap(peersBacking);
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
	
	@Override
	public void onStart()
	{
		super.onStart();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		
		Log.v(TAG, "RESUME");
		
        ip = getIpAddress();
        View v = findViewById(R.id.ip);
        if(v instanceof TextView) {
        	((TextView)v).setText(ip.getHostAddress());
        }
        broadcast = getBroadcast(ip);
        binaryMessage.reset();
        try {
			binaryMessage.writeInt(PKT_HELLO);
		} catch (IOException e2) {
		}
        byte[] message = binaryMessage.getWrittenCopy();
        Log.v(TAG, "Offset: " + binaryMessage.getOffset() + "|" + binaryMessage.limit() + "|" + binaryMessage.capa());
        
		helloWorldPacket = new DatagramPacket(message, binaryMessage.writtenLength(), broadcast, UDP_PORT);
		byte[] buffer = new byte[100];
		dataPacket = new DatagramPacket(buffer, buffer.length, broadcast, UDP_PORT);
        try {
			sendSocket = new DatagramSocket();
			sendSocket.setBroadcast(true);
		} catch (SocketException e1) {
			Log.v(TAG, "Could not create socket: " + e1.getMessage(), e1);
			finish();
		}
        
//		setStrictMode();

		Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					start_UDP();
				} catch (IOException e) {
					Log.v(TAG, e.getMessage());
				}
			}
		});
		thread.setName("UDPreceiver");
		thread.setPriority(Thread.MAX_PRIORITY);
		thread.start();
		
		sendThread = new Thread("UDPsender") {
			public void run() {
				while(!isInterrupted()) {
					try {
						sendUDPMessage(sendQueue.take());
					} catch (InterruptedException e) {
						Log.v(TAG, "Exception when taking from queue: " + e.getMessage(), e);
					}
				}
			}
		};
		sendThread.setPriority(Thread.MAX_PRIORITY);
		sendThread.start();
		
		paceThread = new Thread("PaceThread") {
			public void run() {
				int cnt = 0;
				while(!isInterrupted()) {
					SystemClock.sleep(40);
					if((cnt++) % 100 == 10) {
						sendUDPMessage(helloWorldPacket);
					}
					else
					{
						for(InetAddress ia : peers) {
							binaryMessage.reset();
							try {
								binaryMessage.writeInt(PKT_PING).writeInt(pingCounter).writeLong(System.nanoTime());
								sendUDPMessage(packet(binaryMessage.getWritten(), ia));
							} catch (IOException e) {
								Log.v(TAG, "Problem sending package: " + e.getMessage());
							}
						}
					}
				}
			}
		};
		paceThread.setPriority(Thread.MAX_PRIORITY);
		paceThread.start();
		
		WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if(wm != null) {
			ml = wm.createMulticastLock("");
			ml.acquire();
		}	
	}
	
	private DatagramPacket packet(byte[] msg, InetAddress address)
	{
		dataPacket.setData(msg);
		dataPacket.setAddress(address);
		return dataPacket;
	}
	
	@SuppressLint("NewApi")
	private void setStrictMode() {
		//Strict mode
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
        .detectDiskReads()
        .detectDiskWrites()
        .detectNetwork()   // or .detectAll() for all detectable problems
        .penaltyLog()
        .build());
        
        if (Build.VERSION.SDK_INT >= 11) {
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
			        .detectLeakedSqlLiteObjects()
			        .detectLeakedClosableObjects()
			        .penaltyLog()
			        .penaltyDeath()
			        .build());
        }
	}
	
	@Override
	public void onPause()
	{
		super.onPause();

		Log.v(TAG, "PAUSE");
		
		paceThread.interrupt();

		ml.release();
		
		if(serverSocketUDP != null) {
			serverSocketUDP.close();
		}

		if(sendSocket != null) {
			sendSocket.close();
		}
	}
	
	@Override
	public void onStop()
	{
		super.onStop();	
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	final List<InetAddress> rowHeaders = new LinkedList<InetAddress>();
	final List<InetAddress> colHeaders = new LinkedList<InetAddress>();
	final byte[] receiveData = new byte[UDP_MAX_SIZE];
	final DatagramPacket receivePacket = new DatagramPacket(
			receiveData, receiveData.length);
	
	private Map<PeerPair, TextView> viewCache = new HashMap<PeerPair, TextView>(); 
	
	private void start_UDP() throws IOException {
		try {
			serverSocketUDP = new DatagramSocket(UDP_PORT);
		} catch (Exception e) {
			Log.w(TAG, "Exception opening DatagramSocket UDP: " + e.getMessage());
			return;
		}

		while (true) {
			serverSocketUDP.receive(receivePacket);

			if (!receivePacket.getAddress().equals(ip)) {
				
				binaryMessage.parseFrom(receivePacket.getData());
				int firstInt = binaryMessage.readInt();
				if(firstInt == PKT_HELLO) {
					if(!peers.contains(receivePacket.getAddress())) {
						Log.v(TAG, "Got package from new PEER: " + receivePacket.getAddress());
						peers.add(receivePacket.getAddress());
						runOnUiThread(new Runnable() {
							public void run() {
								View v = findViewById(R.id.ping);
								if(v instanceof TextView) {
									((TextView)v).setText(""+peers.size());
								}
							}
						});
					}
				}
				else if(firstInt == PKT_PING)
				{
					final long now = System.nanoTime();
					int which = binaryMessage.readInt();
					long timer = binaryMessage.readLong();
					binaryMessage.reset();
					binaryMessage.writeInt(PKT_PONG).writeInt(which).writeLong(timer).writeLong(now);
					
					sendUDPMessage(packet(binaryMessage.getWritten(), receivePacket.getAddress()));
				}

				if(firstInt == PKT_PONG && pairTemp != null) {
					final long sent = binaryMessage.readLong();
					
					final long now = System.nanoTime();
					pairTemp.setPeer1(ip);
					pairTemp.setPeer2(receivePacket.getAddress());
					final PeerPair pp = pairTemp;
					if(!latencies.containsKey(pairTemp))
					{
						PeerPair newKey = new PeerPair(ip, receivePacket.getAddress());
						Latency l = new Latency();
						l.to = (now-sent)/1000000;
						latencies.put(newKey,  l);
						updateUI(sent, now, newKey);
					}
					else
					{
						latencies.get(pp).to = (now-sent)/1000000;
						updateUI(sent, now, pp);
					}
				}
			} else {
			}
		}// while ends
	}// method ends

	private void updateUI(final long sent, final long now, final PeerPair pp) {
		runOnUiThread(new Runnable() {
			public void run() {
				if(viewCache.containsKey(pp)) {
					TextView textView = viewCache.get(pp);
					if(textView != null) {
						textView.setText(Long.toString((now-sent)/1000000));
					}
					else
					{
						Log.v(TAG, "FOUND NULL! " + pp);
					}
					return;
				}
				viewCache.clear();
				View v = findViewById(R.id.tableLayout);

				rowHeaders.clear();
				colHeaders.clear();
				
				rowHeaders.add(ip);

				colHeaders.addAll(peers);
				
				Collections.sort(colHeaders, new Comparator<InetAddress>() {
					@Override
					public int compare(InetAddress lhs, InetAddress rhs) {
						return PeerPair.compareAddr(lhs, rhs);
					}
				});
				
				Collections.sort(rowHeaders, new Comparator<InetAddress>() {
					@Override
					public int compare(InetAddress lhs, InetAddress rhs) {
						return PeerPair.compareAddr(lhs, rhs);
					}
				});
				
				if(v instanceof TableLayout) {
					TableLayout tl = ((TableLayout)v);
					tl.removeAllViews();

					TableRow header = new TableRow(getApplicationContext());
					header.addView(new TextView(getApplicationContext()));
					for(InetAddress ia : colHeaders) {
						TextView textView = new TextView(getApplicationContext());
						textView.setPadding(7,  2,  7,  2);
						textView.setText(ia.getHostAddress());
						textView.setTextSize(20.0f);

						if(!ia.getHostAddress().equals(ip.getHostAddress())) {
							textView.setTextColor(0xff000000);
						} else {
							textView.setTextColor(0xffff0000);
						}
						header.addView(textView);
					}
					tl.addView(header);
					
					for(InetAddress ia : rowHeaders) {
						TableRow tableRow = new TableRow(getApplicationContext());
						TextView textView = new TextView(getApplicationContext());
						textView.setText(ia.getHostAddress());
						textView.setPadding(0,  6,  6,  6);
						textView.setTextSize(20.0f);
						if(!ia.getHostAddress().equals(ip.getHostAddress())) {
							textView.setTextColor(0xff000000);
						} else {
							textView.setTextColor(0xffff0000);
						}
						tableRow.addView(textView);
						
						for(PeerPair everyPP : latencies.keySet()) {
							InetAddress peer = null;
							if(everyPP.getPeer1().equals(ia)) {
								peer = everyPP.getPeer2();
							} else if(everyPP.getPeer2().equals(ia)) {
								peer = everyPP.getPeer1();
							}

							if(peer != null) {
								TextView tv = new TextView(getApplicationContext());
								tv.setPadding(7,  2,  7,  2);
								tv.setTextColor(0xff000000);
								tv.setText(Long.toString(latencies.get(everyPP).to));
								tv.setTextSize(20.0f);
								viewCache.put(everyPP, tv);
								tableRow.addView(tv);
							}
							else
							{
								tableRow.addView(new View(getApplicationContext()));
							}
						
						}
						
						tl.addView(tableRow);
					}
				}
				
				rowHeaders.clear();
				colHeaders.clear();
			}
		});
	}

	private void sendUDPMessage(DatagramPacket packet) {
		try {
			sendSocket.send(packet);
		} catch (Exception e) {
			Log.d(TAG, "Exception packet broadcast: " + e.getMessage() +"|" + packet, e);
		}
	}

	public InetAddress getIpAddress() {
		try {

			InetAddress inetAddress = null;
			InetAddress myAddr = null;

			for (Enumeration<NetworkInterface> networkInterface = NetworkInterface
					.getNetworkInterfaces(); networkInterface.hasMoreElements();) {

				NetworkInterface singleInterface = networkInterface
						.nextElement();

				for (Enumeration<InetAddress> IpAddresses = singleInterface
						.getInetAddresses(); IpAddresses.hasMoreElements();) {
					inetAddress = IpAddresses.nextElement();

					if (!inetAddress.isLoopbackAddress()
							&& (singleInterface.getDisplayName().contains(
									"wlan0") || singleInterface
									.getDisplayName().contains("eth0"))) {

						myAddr = inetAddress;
					}
				}
			}
			Log.v(TAG, "My ip is " + myAddr);
			return myAddr;

		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return null;
	}

	public InetAddress getBroadcast(InetAddress inetAddr) {

		Log.v(TAG, "getBroadcast");
		NetworkInterface temp;
		InetAddress iAddr = null;
		try {
			temp = NetworkInterface.getByInetAddress(inetAddr);
			List<InterfaceAddress> addresses = temp.getInterfaceAddresses();

			for (InterfaceAddress inetAddress : addresses) {
				iAddr = inetAddress.getBroadcast();
			}
			Log.d(TAG, "iAddr=" + iAddr);
			return iAddr;

		} catch (SocketException e) {

			e.printStackTrace();
			Log.d(TAG, "getBroadcast" + e.getMessage());
		}
		return null;
	}
}
