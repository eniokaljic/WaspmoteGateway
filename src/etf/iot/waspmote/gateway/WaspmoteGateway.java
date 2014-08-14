package etf.iot.waspmote.gateway;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

import org.apache.log4j.Logger;

class WaspmoteGateway {
	private String openhabHost;

	public String getOpenhabHost() {
		return openhabHost;
	}

	public void setOpenhabHost(String openhabHost) {
		this.openhabHost = openhabHost;
	}

	public WaspmoteGateway(String openhabHost) {
		this.openhabHost = openhabHost;
	}

	public void postValue(String key, String value) throws Exception {
		URL u = new URL("http://" + openhabHost + "/rest/items/" + key);
		HttpURLConnection conn = (HttpURLConnection) u.openConnection();
		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Host", openhabHost);
		conn.setRequestProperty("Content-Type", "text/plain");
		conn.setRequestProperty("Authorization", "Basic cm9vdDptb3NxdWl0dG8=");
		conn.setRequestProperty("Cache-Control", "no-cache");
		conn.setRequestProperty("Accept", "*/*");
		conn.setRequestProperty("Content-Length", String.valueOf(value.length()));

		OutputStream os = conn.getOutputStream();
		os.write(value.getBytes());
		os.flush();
		os.close();

		InputStream is = conn.getInputStream();
		BufferedReader rd = new BufferedReader(new InputStreamReader(is));
		String line;
		StringBuffer response = new StringBuffer();
		while ((line = rd.readLine()) != null) {
			response.append(line);
			response.append('\r');
		}
		rd.close();
	}

	public void processPacket(byte[] packet) {
		String rawPacket = new String(packet);
		int payloadBeginIndex = rawPacket.indexOf("<=>");
		String payload = rawPacket.substring(payloadBeginIndex);
		Logger.getLogger("").debug("Payload received: " + payload);
		String params[] = payload.split("#");
		for (String param : params) {
			if (param.contains(":")) {
				try {
					String key = param.split(":")[0];
					String value = param.split(":")[1];
					Logger.getLogger("").debug("Sending new value to OpenHAB server [" + key + " = " + value + "]");
					postValue(key, value);
				} catch (Exception ex) {
					Logger.getLogger("").warn(ex.getMessage());
				}
			}
		}
	}

	public static void main(String args[]) throws Exception {
		// Loading properties file
		String propertiesFileName;
		if (args.length == 0) {
			propertiesFileName = "config.properties";
			System.out.println("Using default properties file [config.properties]");
		} else {
			propertiesFileName = args[0];
			System.out.println("Using properties file [" + propertiesFileName + "]");
		}
		InputStream propertiesFile = new FileInputStream(propertiesFileName);
		Properties properties = new Properties();
		properties.load(propertiesFile);
		// Read properties
		final String OpenHabHost = properties.getProperty("OpenHabHost");
		Logger.getLogger("").info("OpenHabHost = " + OpenHabHost);
		final String SerialPort = properties.getProperty("SerialPort");
		Logger.getLogger("").info("SerialPort = " + SerialPort);
		final int BaudRate = Integer.parseInt(properties.getProperty("BaudRate"));
		Logger.getLogger("").info("BaudRate = " + BaudRate);
		final int TcpServerPort = Integer.parseInt(properties.getProperty("TcpServerPort"));
		Logger.getLogger("").info("TcpServerPort = " + TcpServerPort);
		// Close properties file
		propertiesFile.close();

		WaspmoteGateway waspmoteGateway = new WaspmoteGateway(OpenHabHost);
		if (!SerialPort.isEmpty()) {
			SerialTransportLayer serialTransportLayer = new SerialTransportLayer(SerialPort, BaudRate);
			serialTransportLayer.setWaspmoteGateway(waspmoteGateway);
			serialTransportLayer.start();
		}
		if (TcpServerPort != 0) {
			TcpTransportLayer tcpTransportLayer = new TcpTransportLayer(TcpServerPort);
			tcpTransportLayer.setWaspmoteGateway(waspmoteGateway);
			tcpTransportLayer.start();
		}
	}
}