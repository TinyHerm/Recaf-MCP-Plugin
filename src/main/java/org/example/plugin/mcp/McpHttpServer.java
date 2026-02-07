package org.example.plugin.mcp;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class McpHttpServer implements Closeable {

	private final int port;
	private final Function<String, String> handler;
	private ServerSocket serverSocket;
	private ExecutorService pool;
	private volatile boolean running;

	public McpHttpServer(int port, Function<String, String> handler) {
		this.port = port;
		this.handler = handler;
	}

	public void start() throws IOException {
		serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
		pool = Executors.newVirtualThreadPerTaskExecutor();
		running = true;
		pool.submit(this::acceptLoop);
	}

	private void acceptLoop() {
		while (running) {
			try {
				Socket socket = serverSocket.accept();
				pool.submit(() -> handleConnection(socket));
			} catch (IOException e) {
				if (running) e.printStackTrace();
			}
		}
	}

	private void handleConnection(Socket socket) {
		try (socket) {
			socket.setSoTimeout(30000);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();

			HttpRequest req = parseRequest(in);
			if (req == null) {
				sendResponse(out, 400, "Bad Request", "text/plain", "Malformed request");
				return;
			}

			String origin = req.headers.get("origin");
			if (origin != null) {
				try {
					java.net.URI uri = java.net.URI.create(origin);
					String host = uri.getHost();
					if (host != null && !host.equals("127.0.0.1") && !host.equals("localhost") && !host.equals("[::1]")) {
						sendResponse(out, 403, "Forbidden", "text/plain", "Origin not allowed");
						return;
					}
				} catch (Exception e) {
					sendResponse(out, 403, "Forbidden", "text/plain", "Invalid origin");
					return;
				}
			}

			if (req.method.equals("OPTIONS")) {
				sendCorsResponse(out);
				return;
			}

			if (!req.method.equals("POST") || !req.path.equals("/mcp")) {
				sendResponse(out, 404, "Not Found", "text/plain", "POST /mcp only");
				return;
			}

			String responseBody = handler.apply(req.body);
			sendJsonResponse(out, responseBody);
		} catch (Exception e) {
			// connection-level errors
		}
	}

	private record HttpRequest(String method, String path, Map<String, String> headers, String body) {}

	private HttpRequest parseRequest(InputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String requestLine = reader.readLine();
		if (requestLine == null || requestLine.isBlank()) return null;

		String[] parts = requestLine.split(" ");
		if (parts.length < 2) return null;

		String method = parts[0];
		String path = parts[1];
		Map<String, String> headers = new HashMap<>();
		String line;
		while ((line = reader.readLine()) != null && !line.isEmpty()) {
			int colon = line.indexOf(':');
			if (colon > 0) {
				headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
			}
		}

		String body = "";
		String contentLength = headers.get("content-length");
		if (contentLength != null) {
			int len = Integer.parseInt(contentLength.trim());
			char[] buf = new char[len];
			int read = 0;
			while (read < len) {
				int r = reader.read(buf, read, len - read);
				if (r == -1) break;
				read += r;
			}
			body = new String(buf, 0, read);
		}

		return new HttpRequest(method, path, headers, body);
	}

	private void sendResponse(OutputStream out, int status, String statusText, String contentType, String body) throws IOException {
		byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
		String header = "HTTP/1.1 " + status + " " + statusText + "\r\n"
				+ "Content-Type: " + contentType + "\r\n"
				+ "Content-Length: " + bodyBytes.length + "\r\n"
				+ "Connection: close\r\n"
				+ corsHeaders()
				+ "\r\n";
		out.write(header.getBytes(StandardCharsets.UTF_8));
		out.write(bodyBytes);
		out.flush();
	}

	private void sendJsonResponse(OutputStream out, String json) throws IOException {
		sendResponse(out, 200, "OK", "application/json", json);
	}

	private void sendCorsResponse(OutputStream out) throws IOException {
		String header = "HTTP/1.1 204 No Content\r\n"
				+ "Content-Length: 0\r\n"
				+ "Connection: close\r\n"
				+ corsHeaders()
				+ "\r\n";
		out.write(header.getBytes(StandardCharsets.UTF_8));
		out.flush();
	}

	private String corsHeaders() {
		return "Access-Control-Allow-Origin: *\r\n"
				+ "Access-Control-Allow-Methods: POST, OPTIONS\r\n"
				+ "Access-Control-Allow-Headers: Content-Type, Authorization\r\n";
	}

	@Override
	public void close() {
		running = false;
		try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
		if (pool != null) pool.shutdownNow();
	}
}
