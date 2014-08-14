package etf.iot.waspmote.gateway;

import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.log4j.Logger;

public class TcpTransportLayer implements Runnable, Cloneable {
	private WaspmoteGateway waspmoteGateway;
	private int port;
	private Thread runner = null;
	private ServerSocket server = null;
	private Socket data = null;
	private boolean done = false;

	private static int RTO = 50;

	public TcpTransportLayer(int port) {
		this.port = port;
	}

	public WaspmoteGateway getWaspmoteGateway() {
		return waspmoteGateway;
	}

	public void setWaspmoteGateway(WaspmoteGateway waspmoteGateway) {
		this.waspmoteGateway = waspmoteGateway;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getPort() {
		return port;
	}

	public void start() {
		if (runner == null) {
			this.setPort(port);
			try {
				server = new ServerSocket(getPort());
			} catch (Exception ex) {
				Logger.getLogger("").warn(ex.getMessage());
			}
			runner = new Thread(this);
			runner.start();
			Logger.getLogger("").info("TCP transport layer started");
		}
	}

	public void stop() {
		done = true;
		try {
			Thread.sleep(500);
			server.close();
		} catch (Exception ex) {
			Logger.getLogger("").warn(ex.getMessage());
		}
		runner.interrupt();
		Logger.getLogger("").info("TCP transport layer stopped");
	}

	protected synchronized boolean getDone() {
		return done;
	}

	private void debugPrint(String message, byte[] temp, int length) {
		String tempString = message;
		for (int i = 0; i < length; i++)
			tempString += String.format("%02X ", temp[i]);
		Logger.getLogger("").debug(tempString);
	}

	@Override
	public void run() {
		if (server != null) {
			while (!getDone()) {
				try {
					Socket datasocket = server.accept();
					datasocket.setSoTimeout(1);
					TcpTransportLayer newSocket = (TcpTransportLayer) clone();
					newSocket.server = null;
					newSocket.data = datasocket;
					newSocket.runner = new Thread(newSocket);
					newSocket.runner.start();
					Logger.getLogger("").debug("TCP connection from " + datasocket.getRemoteSocketAddress() + " established");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			run(data);
		}
	}

	public synchronized byte[] receive(DataInputStream dis) throws Exception {
		byte[] readBuffer = new byte[512];
		int readBufferLen = 0;
		long startTime = System.currentTimeMillis();
		long elapsedTime = 0L;
		while (elapsedTime < RTO) {
			if (dis.available() > 0) {
				readBuffer[readBufferLen] = (byte) dis.read();
				readBufferLen++;
				startTime = System.currentTimeMillis();
			}
			elapsedTime = System.currentTimeMillis() - startTime;
		}
		byte[] retVal = new byte[readBufferLen];
		System.arraycopy(readBuffer, 0, retVal, 0, readBufferLen);
		debugPrint("Recv: ", retVal, retVal.length);
		return retVal;
	}

	public void run(Socket connectionSocket) {
		try {
			DataInputStream dis = new DataInputStream(connectionSocket.getInputStream());
			while (!connectionSocket.isClosed() && connectionSocket.isConnected() && !connectionSocket.isInputShutdown() && connectionSocket.isBound() && !connectionSocket.isOutputShutdown()) {
				try {
					if (dis.available() > 0) {
						byte[] packet = receive(dis);
						waspmoteGateway.processPacket(packet);
					} else {
						try {
							dis.read();
						} catch (SocketTimeoutException ex) {
							//NOP
						} catch (SocketException ex) {
							break;
						}
					}
					Thread.sleep(10);					
				} catch (Exception ex) {			
					ex.printStackTrace();
					Logger.getLogger("").warn(ex.getMessage());
				}
			}
			connectionSocket.close();
			Logger.getLogger("").debug("TCP connection from " + connectionSocket.getRemoteSocketAddress() + " closed");
		} catch (Exception ex) {
			Logger.getLogger("").warn(ex.getMessage());
		}
	}
}
