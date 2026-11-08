package io.mockhub.grpc;

import java.util.ArrayList;
import java.util.List;

final class ProtoTokens {
  enum Kind { WORD, NUMBER, STRING, SYMBOL, END }

  record Token(Kind kind, String text, int line) {
    boolean is(String value) {
      return (kind == Kind.SYMBOL || kind == Kind.WORD) && text.equals(value);
    }
  }

  static final int MAX_TOKENS = 200_000;

  private final List<Token> tokens;
  private int at;

  ProtoTokens(String text) {
    this.tokens = tokenize(text);
  }

  Token peek() {
    return tokens.get(at);
  }

  Token peek(int ahead) {
    return tokens.get(Math.min(at + ahead, tokens.size() - 1));
  }

  Token next() {
    Token t = tokens.get(at);
    if (t.kind() != Kind.END) at++;
    return t;
  }

  boolean accept(String value) {
    if (!peek().is(value)) return false;
    next();
    return true;
  }

  Token expect(String value) {
    Token t = next();
    if (!t.is(value)) throw error(t, "Expected '" + value + "' but found " + describe(t));
    return t;
  }

  String word() {
    Token t = next();
    if (t.kind() != Kind.WORD) throw error(t, "Expected a name but found " + describe(t));
    return t.text();
  }

  String dottedName() {
    StringBuilder name = new StringBuilder();
    if (accept(".")) name.append('.');
    name.append(word());
    while (peek().is(".")) {
      next();
      name.append('.').append(word());
    }
    return name.toString();
  }

  int integer() {
    boolean negative = accept("-");
    Token t = next();
    if (t.kind() != Kind.NUMBER) throw error(t, "Expected a number but found " + describe(t));
    try {
      long value = parseInteger(t.text());
      value = negative ? -value : value;
      if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw error(t, "The number " + t.text() + " is too large");
      return (int) value;
    } catch (NumberFormatException e) {
      throw error(t, "Expected a whole number but found " + t.text());
    }
  }

  String string() {
    Token t = next();
    if (t.kind() != Kind.STRING) throw error(t, "Expected a quoted string but found " + describe(t));
    StringBuilder value = new StringBuilder(t.text());
    while (peek().kind() == Kind.STRING) value.append(next().text());
    return value.toString();
  }

  GrpcException error(Token at, String message) {
    return new GrpcException("Line " + at.line() + ": " + message);
  }

  static String describe(Token t) {
    return switch (t.kind()) {
      case END -> "the end of the file";
      case STRING -> "a string";
      default -> "'" + t.text() + "'";
    };
  }

  private static long parseInteger(String text) {
    String lower = text.toLowerCase();
    if (lower.startsWith("0x")) return Long.parseLong(text.substring(2), 16);
    if (lower.length() > 1 && lower.startsWith("0") && lower.chars().allMatch(Character::isDigit)) return Long.parseLong(text.substring(1), 8);
    return Long.parseLong(text);
  }

  private static List<Token> tokenize(String text) {
    List<Token> out = new ArrayList<>();
    int line = 1;
    int i = 0;
    int n = text.length();
    while (i < n) {
      char c = text.charAt(i);
      if (c == '\n') {
        line++;
        i++;
      } else if (Character.isWhitespace(c)) {
        i++;
      } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
        while (i < n && text.charAt(i) != '\n') i++;
      } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
        int end = text.indexOf("*/", i + 2);
        if (end < 0) throw new GrpcException("Line " + line + ": A /* comment is never closed");
        for (int k = i; k < end; k++) if (text.charAt(k) == '\n') line++;
        i = end + 2;
      } else if (Character.isLetter(c) || c == '_') {
        int start = i;
        while (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
        out.add(new Token(Kind.WORD, text.substring(start, i), line));
      } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(text.charAt(i + 1)))) {
        int start = i;
        while (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '.'
            || ((text.charAt(i) == '-' || text.charAt(i) == '+') && (text.charAt(i - 1) == 'e' || text.charAt(i - 1) == 'E') && !text.substring(start, i).toLowerCase().startsWith("0x")))) i++;
        out.add(new Token(Kind.NUMBER, text.substring(start, i), line));
      } else if (c == '"' || c == '\'') {
        StringBuilder value = new StringBuilder();
        i++;
        while (true) {
          if (i >= n || text.charAt(i) == '\n') throw new GrpcException("Line " + line + ": A string is never closed");
          char s = text.charAt(i);
          if (s == c) {
            i++;
            break;
          }
          if (s == '\\' && i + 1 < n) {
            char e = text.charAt(i + 1);
            value.append(switch (e) {
              case 'n' -> '\n';
              case 't' -> '\t';
              case 'r' -> '\r';
              case '0' -> '\0';
              default -> e;
            });
            i += 2;
          } else {
            value.append(s);
            i++;
          }
        }
        out.add(new Token(Kind.STRING, value.toString(), line));
      } else if ("{}()[]<>;=,.:-+/".indexOf(c) >= 0) {
        out.add(new Token(Kind.SYMBOL, String.valueOf(c), line));
        i++;
      } else {
        throw new GrpcException("Line " + line + ": Unexpected character '" + c + "'");
      }
      if (out.size() > MAX_TOKENS) throw new GrpcException("The .proto file is too long");
    }
    out.add(new Token(Kind.END, "", line));
    return out;
  }
}
