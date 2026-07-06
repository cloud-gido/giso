package com.giso.gateway;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** 极简 multipart/form-data 解析（单文件上传够用）。 */
final class MultipartForm {
  private MultipartForm() { }

  record Part(String name, String filename, String contentType, byte[] data) { }

  static Map<String, Part> parse(byte[] body, String contentType) {
    Map<String, Part> out = new HashMap<>();
    if (body == null || contentType == null) return out;
    String ct = contentType.toLowerCase();
    int bi = ct.indexOf("boundary=");
    if (bi < 0) return out;
    String boundary = contentType.substring(bi + "boundary=".length()).trim();
    if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
      boundary = boundary.substring(1, boundary.length() - 1);
    }
    byte[] delim = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
    int pos = indexOf(body, delim, 0);
    while (pos >= 0) {
      int start = pos + delim.length;
      if (start + 2 <= body.length && body[start] == '-' && body[start + 1] == '-') break;
      if (start < body.length && body[start] == '\r') start++;
      if (start < body.length && body[start] == '\n') start++;
      int next = indexOf(body, delim, start);
      if (next < 0) next = body.length;
      int partEnd = next;
      if (partEnd >= 2 && body[partEnd - 2] == '\r' && body[partEnd - 1] == '\n') partEnd -= 2;
      parsePart(body, start, partEnd, out);
      pos = next;
    }
    return out;
  }

  private static void parsePart(byte[] body, int start, int end, Map<String, Part> out) {
    int hdrEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), start);
    if (hdrEnd < 0 || hdrEnd > end) return;
    String headers = new String(body, start, hdrEnd - start, StandardCharsets.UTF_8);
    int dataStart = hdrEnd + 4;
    if (dataStart > end) return;
    String name = null;
    String filename = null;
    String partCt = null;
    for (String line : headers.split("\r\n")) {
      if (line.toLowerCase().startsWith("content-disposition:")) {
        for (String token : line.substring("content-disposition:".length()).split(";")) {
          String t = token.trim();
          if (t.startsWith("name=")) name = unquote(t.substring(5));
          else if (t.startsWith("filename=")) filename = unquote(t.substring(9));
        }
      } else if (line.toLowerCase().startsWith("content-type:")) {
        partCt = line.substring("content-type:".length()).trim();
      }
    }
    if (name == null) return;
    byte[] data = new byte[end - dataStart];
    System.arraycopy(body, dataStart, data, 0, data.length);
    out.put(name, new Part(name, filename, partCt, data));
  }

  private static String unquote(String s) {
    String v = s.trim();
    if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) return v.substring(1, v.length() - 1);
    return v;
  }

  private static int indexOf(byte[] haystack, byte[] needle, int from) {
    if (needle.length == 0 || haystack.length < needle.length) return -1;
    outer:
    for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) continue outer;
      }
      return i;
    }
    return -1;
  }
}
