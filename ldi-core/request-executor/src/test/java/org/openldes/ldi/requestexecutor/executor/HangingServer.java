package org.openldes.ldi.requestexecutor.executor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test server that accepts every connection but never sends a single byte back, like an overloaded upstream that
 * keeps the connection open without answering.
 */
public class HangingServer implements AutoCloseable {

	private final ServerSocket serverSocket;
	private final List<Socket> acceptedSockets = new CopyOnWriteArrayList<>();

	public HangingServer() throws IOException {
		serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		final Thread acceptor = new Thread(this::acceptForever, "hanging-server");
		acceptor.setDaemon(true);
		acceptor.start();
	}

	public String url(String path) {
		return "http://localhost:" + serverSocket.getLocalPort() + path;
	}

	private void acceptForever() {
		while (!serverSocket.isClosed()) {
			try {
				acceptedSockets.add(serverSocket.accept());
			} catch (IOException e) {
				return;
			}
		}
	}

	@Override
	public void close() throws IOException {
		serverSocket.close();
		for (Socket socket : acceptedSockets) {
			socket.close();
		}
	}

}
