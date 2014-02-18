package se.sandos.android;

import java.math.BigInteger;
import java.net.InetAddress;

import android.util.Log;

public class PeerPair {
	private InetAddress peer1;
	private InetAddress peer2;

	private static final String TAG = "majs";

	public PeerPair() {
	}
	
	public PeerPair(InetAddress peer1, InetAddress peer2) {
		//Sort peers, makes us automatically equate pairs like (A,B) and (B,A)
		byte[] p1 = new byte[5];
		System.arraycopy(peer1.getAddress(), 0, p1, 1, 4);
		byte[] p2 = new byte[5];
		System.arraycopy(peer2.getAddress(), 0, p2, 1, 4);
		BigInteger pb1 = new BigInteger(p1);
		BigInteger pb2 = new BigInteger(p2);
		if(pb1.compareTo(pb2) > 0)
		{
			this.peer2 = peer1;
			this.peer1 = peer2;
		}
		else
		{
			this.peer1 = peer1;
			this.peer2 = peer2;
		}
	}

	public static int compareAddr(InetAddress addr1, InetAddress addr2)
	{
		byte[] p1 = new byte[5];
		System.arraycopy(addr1.getAddress(), 0, p1, 1, 4);
		byte[] p2 = new byte[5];
		System.arraycopy(addr2.getAddress(), 0, p2, 1, 4);
		BigInteger pb1 = new BigInteger(p1);
		BigInteger pb2 = new BigInteger(p2);
		return pb1.compareTo(pb2);
	}
	
	public InetAddress getPeer1() {
		return peer1;
	}

	public InetAddress getPeer2() {
		return peer2;
	}

	public void setPeer1(InetAddress peer1) {
		this.peer1 = peer1;
	}

	public void setPeer2(InetAddress peer2) {
		this.peer2 = peer2;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((peer1 == null) ? 0 : peer1.hashCode());
		result = prime * result + ((peer2 == null) ? 0 : peer2.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PeerPair other = (PeerPair) obj;
		if (peer1 == null) {
			if (other.peer1 != null)
				return false;
		} else if (!peer1.equals(other.peer1))
			return false;
		if (peer2 == null) {
			if (other.peer2 != null)
				return false;
		} else if (!peer2.equals(other.peer2))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "PeerPair [peer1=" + peer1 + ", peer2=" + peer2 + "]";
	}
	
	
}
