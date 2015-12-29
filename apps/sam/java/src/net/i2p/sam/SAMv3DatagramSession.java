/**
 * @author MKVore
 *
 */

package net.i2p.sam;

import java.io.IOException;
import java.util.Properties;

import net.i2p.client.I2PSessionException;
import net.i2p.data.DataFormatException;
import net.i2p.data.DataHelper;
import net.i2p.data.Destination;
import net.i2p.util.Log;

import java.net.InetSocketAddress;
import java.net.SocketAddress ;
import java.nio.ByteBuffer;

class SAMv3DatagramSession extends SAMDatagramSession implements SAMv3Handler.Session, SAMDatagramReceiver {
	
	private final SAMv3Handler handler;
	private final SAMv3DatagramServer server;
	private final String nick;
	private final SocketAddress clientAddress;
	
	public String getNick() { return nick; }

	/**
	 *   build a DatagramSession according to informations registered
	 *   with the given nickname
	 *
	 * @param nick nickname of the session
	 * @throws IOException
	 * @throws DataFormatException
	 * @throws I2PSessionException
	 */
	public SAMv3DatagramSession(String nick, SAMv3DatagramServer dgServer) 
			throws IOException, DataFormatException, I2PSessionException, SAMException {
		super(SAMv3Handler.sSessionsHash.get(nick).getDest(),
				SAMv3Handler.sSessionsHash.get(nick).getProps(),
				null  // to be replaced by this
				);
		this.nick = nick;
		this.recv = this;  // replacement
		this.server = dgServer;

		SAMv3Handler.SessionRecord rec = SAMv3Handler.sSessionsHash.get(nick);
		if (rec == null)
			throw new SAMException("Record disappeared for nickname : \""+nick+"\"");

		this.handler = rec.getHandler();
		
		Properties props = rec.getProps();
		String portStr = props.getProperty("PORT");
		if (portStr == null) {
			if (_log.shouldDebug())
				_log.debug("receiver port not specified. Current socket will be used.");
			this.clientAddress = null;
		} else {
			int port = Integer.parseInt(portStr);
			String host = props.getProperty("HOST");
			if (host == null) {    		
				host = rec.getHandler().getClientIP();
				if (_log.shouldDebug())
					_log.debug("no host specified. Taken from the client socket : " + host+':'+port);
			}
			this.clientAddress = new InetSocketAddress(host, port);
		}
	}

	public void receiveDatagramBytes(Destination sender, byte[] data, int proto,
	                                 int fromPort, int toPort) throws IOException {
		if (this.clientAddress==null) {
			this.handler.receiveDatagramBytes(sender, data, proto, fromPort, toPort);
		} else {
			StringBuilder buf = new StringBuilder(600);
			buf.append(sender.toBase64());
			if ((handler.verMajor == 3 && handler.verMinor >= 2) || handler.verMajor > 3) {
				buf.append(" FROM_PORT=").append(fromPort).append(" TO_PORT=").append(toPort);
			}
			buf.append('\n');
			String msg = buf.toString();
			ByteBuffer msgBuf = ByteBuffer.allocate(msg.length()+data.length);
			msgBuf.put(DataHelper.getASCII(msg));
			msgBuf.put(data);
			msgBuf.flip();
			this.server.send(this.clientAddress, msgBuf);
		}
	}

	public void stopDatagramReceiving() {
	}
}
