package io.mockhub.bench;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class RawHttpConnection implements AutoCloseable {
  record Response(int status, long bodyBytes, boolean close) {}

  private static final int MAX_LINE = 16 * 1024;

  private final Socket socket;
  private final InputStream in;
  private final OutputStream out;

  RawHttpConnection(String host, int port) throws IOException {
    socket = new Socket();
    socket.setTcpNoDelay(true);
    socket.connect(new InetSocketAddress(host, port), 5000);
    socket.setSoTimeout(30000);
    in = new BufferedInputStream(socket.getInputStream(), 64 * 1024);
    out = new BufferedOutputStream(socket.getOutputStream(), 16 * 1024);
  }

  static byte[] request(String method, String host, String path, String contentType, String body) {
    StringBuilder head = new StringBuilder()
        .append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
        .append("Host: ").append(host).append("\r\n")
        .append("User-Agent: mockhub-bench\r\n")
        .append("Accept: */*\r\n");
    byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
    if (body != null) {
      head.append("Content-Type: ").append(contentType).append("\r\n")
          .append("Content-Length: ").append(payload.length).append("\r\n");
    }
    head.append("\r\n");
    byte[] start = head.toString().getBytes(StandardCharsets.US_ASCII);
    byte[] all = new byte[start.length + payload.length];
    System.arraycopy(start, 0, all, 0, start.length);
    System.arraycopy(payload, 0, all, start.length, payload.length);
    return all;
  }

  Response exchange(byte[] request) throws IOException {
    out.write(request);
    out.flush();
    String status = line();
    if (status == null) throw new EOFException("The server closed the connection");
    String[] parts = status.split(" ", 3);
    if (parts.length < 2 || !parts[0].startsWith("HTTP/1.")) throw new IOException("Not an HTTP/1.x response: " + status);
    int code = Integer.parseInt(parts[1]);
    long length = -1;
    boolean chunked = false;
    boolean close = parts[0].equals("HTTP/1.0");
    for (String header = line(); header != null && !header.isEmpty(); header = line()) {
      int colon = header.indexOf(':');
      if (colon < 0) continue;
      String name = header.substring(0, colon).trim().toLowerCase(Locale.ROOT);
      String value = header.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
      if (name.equals("content-length")) length = Long.parseLong(value);
      else if (name.equals("transfer-encoding")) chunked = value.contains("chunked");
      else if (name.equals("connection")) close = value.contains("close") || (close && !value.contains("keep-alive"));
    }
    long bytes;
    if (chunked) bytes = chunks();
    else if (length >= 0) bytes = skip(length);
    else if (code == 204 || code == 304 || code / 100 == 1) bytes = 0;
    else {
      bytes = untilClosed();
      close = true;
    }
    return new Response(code, bytes, close);
  }

  private long chunks() throws IOException {
    long total = 0;
    while (true) {
      String sizeLine = line();
      if (sizeLine == null) throw new EOFException("The chunked body ended early");
      int semicolon = sizeLine.indexOf(';');
      long size = Long.parseLong((semicolon < 0 ? sizeLine : sizeLine.substring(0, semicolon)).trim(), 16);
      if (size == 0) {
        skipTrailers();
        return total;
      }
      total += skip(size);
      line();
    }
  }

  private void skipTrailers() throws IOException {
    String trailer = line();
    while (trailer != null && !trailer.isEmpty()) trailer = line();
  }

  private long skip(long count) throws IOException {
    long left = count;
    while (left > 0) {
      long skipped = in.skip(left);
      if (skipped <= 0) {
        if (in.read() < 0) throw new EOFException("The body ended early");
        skipped = 1;
      }
      left -= skipped;
    }
    return count;
  }

  private long untilClosed() throws IOException {
    long total = 0;
    byte[] buffer = new byte[8192];
    for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) total += n;
    return total;
  }

  private String line() throws IOException {
    StringBuilder text = new StringBuilder();
    int b = in.read();
    if (b < 0) return null;
    while (b >= 0 && b != '\n') {
      if (b != '\r') text.append((char) b);
      if (text.length() > MAX_LINE) throw new IOException("A header line is too long");
      b = in.read();
    }
    return text.toString();
  }

  @Override
  public void close() {
    try {
      socket.close();
    } catch (IOException alreadyGone) {
      return;
    }
  }
}
