package etf.iot.waspmote.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.log4j.Logger;

import gnu.io.*;

public class SerialTransportLayer implements Runnable {
	private WaspmoteGateway waspmoteGateway;
	private Thread thread;
	private boolean running;
	private String port;
	private int baudRate;
	private SerialPort serialPort;
	private InputStream serialInputStream;
	private OutputStream serialOutputStream;
	
	private static int RTO = 100;

	public SerialTransportLayer(String port, int baudRate) throws Exception {
		this.port = port;
		this.baudRate = baudRate;
	}
	
	public WaspmoteGateway getWaspmoteGateway() {
		return waspmoteGateway;
	}

	public void setWaspmoteGateway(WaspmoteGateway waspmoteGateway) {
		this.waspmoteGateway = waspmoteGateway;
	}
	
	public boolean isRunning() {
		return running;
	}

	public void start() throws Exception {
		if (!running) {
			// Open comm port
			CommPortIdentifier portId = CommPortIdentifier
					.getPortIdentifier(port);
			serialPort = (SerialPort) portId.open("WASPMOTE", baudRate);
			serialPort.setSerialPortParams(baudRate, SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
			serialInputStream = serialPort.getInputStream();
			serialOutputStream = serialPort.getOutputStream();
			// Start receiver thread
			running = true;
			thread = new Thread(this);
			thread.start();
			Logger.getLogger("").info("Serial transport layer started");
		}
	}

	public void stop() throws Exception {
		if (running) {
			running = false;
			Thread.sleep(1000);
			Logger.getLogger("").info("Serial transport layer stopped");
		}
	}

	private void debugPrint(String message, byte[] temp, int length) {
		String tempString = message;
		for (int i = 0; i < length; i++)
			tempString += String.format("%02X ", temp[i]);
		Logger.getLogger("").debug(tempString);
	}

	public void send(byte[] packet) {
		try {
			debugPrint("Sent: ", packet, packet.length);
			serialOutputStream.write(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public synchronized byte[] receive() throws Exception {
		byte[] readBuffer = new byte[256];
		int readBufferLen = 0;
		long startTime = System.currentTimeMillis();
		long elapsedTime = 0L;
		while (elapsedTime < RTO) {
			if (serialInputStream.available() > 0) {
				readBuffer[readBufferLen] = (byte) serialInputStream.read();
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

	@Override
	public void run() {
		while (running) {
			try {
				if (serialInputStream.available() > 0) {
					byte[] packet = receive();
					waspmoteGateway.processPacket(packet);
				}
			} catch (Exception e) {
				Logger.getLogger("").warn(e.getMessage());
			}
		}
		try {
			serialInputStream.close();
			serialOutputStream.close();
		} catch (Exception e) {
			Logger.getLogger("").warn(e.getMessage());
		}
		serialPort.close();
	}
}
