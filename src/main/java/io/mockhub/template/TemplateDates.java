package io.mockhub.template;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TemplateDates {
  static final Map<String, String> SHORTHANDS = Map.ofEntries(
      Map.entry("iso", "YYYY-MM-DDTHH:mm:ss.SSSZ"), Map.entry("iso8601", "YYYY-MM-DDTHH:mm:ss.SSSZ"),
      Map.entry("rfc2822", "ddd, DD MMM YYYY HH:mm:ss ZZ"), Map.entry("rfc3339", "YYYY-MM-DDTHH:mm:ssZ"), Map.entry("w3c", "YYYY-MM-DDTHH:mm:ssZ"),
      Map.entry("unix", "X"), Map.entry("timestamp", "x"), Map.entry("short", "YYYY-MM-DD"), Map.entry("long", "YYYY-MM-DD HH:mm:ss"),
      Map.entry("full", "dddd, MMMM D, YYYY h:mm:ss A"), Map.entry("compact", "YYYYMMDDHHmmss"), Map.entry("utc", "YYYY-MM-DD[T]HH:mm:ss[Z]"),
      Map.entry("sql", "YYYY-MM-DD HH:mm:ss"), Map.entry("us", "MM/DD/YYYY"), Map.entry("eu", "DD/MM/YYYY"));
  private static final List<String> TOKENS = List.of("YYYY", "YY", "MMMM", "MMM", "MM", "M", "DD", "D", "dddd", "ddd", "HH", "H", "hh", "h",
      "mm", "m", "ss", "s", "SSS", "A", "a", "ZZ", "Z");
  private static final Map<String, String> JAVA = Map.ofEntries(
      Map.entry("YYYY", "uuuu"), Map.entry("YY", "uu"), Map.entry("MMMM", "MMMM"), Map.entry("MMM", "MMM"), Map.entry("MM", "MM"), Map.entry("M", "M"),
      Map.entry("DD", "dd"), Map.entry("D", "d"), Map.entry("dddd", "EEEE"), Map.entry("ddd", "EEE"), Map.entry("HH", "HH"), Map.entry("H", "H"),
      Map.entry("hh", "hh"), Map.entry("h", "h"), Map.entry("mm", "mm"), Map.entry("m", "m"), Map.entry("ss", "ss"), Map.entry("s", "s"),
      Map.entry("SSS", "SSS"), Map.entry("A", "a"), Map.entry("a", "a"), Map.entry("ZZ", "xx"), Map.entry("Z", "xxx"));
  private static final Map<String, ChronoUnit> UNITS = Map.ofEntries(
      Map.entry("millisecond", ChronoUnit.MILLIS), Map.entry("second", ChronoUnit.SECONDS), Map.entry("minute", ChronoUnit.MINUTES),
      Map.entry("hour", ChronoUnit.HOURS), Map.entry("day", ChronoUnit.DAYS), Map.entry("week", ChronoUnit.WEEKS),
      Map.entry("month", ChronoUnit.MONTHS), Map.entry("year", ChronoUnit.YEARS));
  private static final List<Function<String, ZonedDateTime>> AUTO = List.of(
      text -> OffsetDateTime.parse(text).atZoneSameInstant(ZoneOffset.UTC),
      text -> LocalDateTime.parse(text).atZone(ZoneOffset.UTC),
      text -> LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC),
      text -> ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).withZoneSameInstant(ZoneOffset.UTC));
  private static final Pattern DURATION_PART = Pattern.compile("[\"']?([A-Za-z]+)[\"']?\\s*[:=]\\s*(-?\\d+)");
  private static final long MILLIS_FROM = 100_000_000_000L;
  private static final long MAX_AMOUNT = 1_000_000;

  private TemplateDates() {}

  static ZonedDateTime parse(Object input, String format) {
    if (input instanceof ZonedDateTime z) return z.withZoneSameInstant(ZoneOffset.UTC);
    if (input instanceof Date d) return d.toInstant().atZone(ZoneOffset.UTC);
    if (input instanceof Instant i) return i.atZone(ZoneOffset.UTC);
    if (input instanceof TemporalAccessor t && !(input instanceof CharSequence)) {
      try {
        return ZonedDateTime.from(t).withZoneSameInstant(ZoneOffset.UTC);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Can't read " + input + " as a date");
      }
    }
    if (input == null) throw new IllegalArgumentException("No date to read");
    String text = input.toString().trim();
    if (format != null && !format.isBlank()) return parseWith(input instanceof Number n && ValueHelpers.whole(n) != null ? String.valueOf(ValueHelpers.whole(n)) : text, format.trim());
    if (input instanceof Number n) {
      Long whole = ValueHelpers.whole(n);
      if (whole == null) throw new IllegalArgumentException("A number date must be whole seconds or milliseconds, not " + n);
      return epoch(whole);
    }
    if (text.matches("-?\\d{1,15}")) return epoch(Long.parseLong(text));
    ZonedDateTime parsed = auto(text);
    if (parsed == null) throw new IllegalArgumentException("Can't read '" + text + "' as a date; use ISO 8601 like 2026-02-25T05:15:05Z");
    return parsed;
  }

  static boolean isDate(Object input) {
    if (input == null || input.toString().isBlank()) return false;
    if (input instanceof Number) return false;
    try {
      parse(input, null);
      return !input.toString().trim().matches("-?\\d+");
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static ZonedDateTime epoch(long value) {
    return (Math.abs(value) >= MILLIS_FROM ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value)).atZone(ZoneOffset.UTC);
  }

  private static ZonedDateTime auto(String text) {
    for (Function<String, ZonedDateTime> parser : AUTO) {
      try {
        return parser.apply(text);
      } catch (DateTimeParseException e) {
        continue;
      }
    }
    return null;
  }

  private static ZonedDateTime parseWith(String text, String format) {
    String pattern = SHORTHANDS.getOrDefault(format.toLowerCase(Locale.ROOT), format);
    if (pattern.equals("X")) return Instant.ofEpochSecond(number(text)).atZone(ZoneOffset.UTC);
    if (pattern.equals("x")) return Instant.ofEpochMilli(number(text)).atZone(ZoneOffset.UTC);
    DateTimeFormatter formatter = formatter(pattern);
    try {
      TemporalAccessor parsed = formatter.parse(text);
      LocalDate date = LocalDate.from(parsed);
      LocalDateTime time = parsed.isSupported(ChronoField.HOUR_OF_DAY) ? LocalDateTime.from(parsed) : date.atStartOfDay();
      ZoneOffset offset = parsed.isSupported(ChronoField.OFFSET_SECONDS) ? ZoneOffset.from(parsed) : ZoneOffset.UTC;
      return time.atOffset(offset).atZoneSameInstant(ZoneOffset.UTC);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("'" + text + "' doesn't match the format " + format);
    }
  }

  private static long number(String text) {
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("'" + text + "' isn't a number of seconds or milliseconds");
    }
  }

  static String format(ZonedDateTime date, String format) {
    String pattern = SHORTHANDS.getOrDefault(format == null || format.isBlank() ? "iso" : format.trim().toLowerCase(Locale.ROOT),
        format == null ? "" : format.trim());
    if (pattern.equals("X")) return String.valueOf(date.toEpochSecond());
    if (pattern.equals("x")) return String.valueOf(date.toInstant().toEpochMilli());
    return formatter(pattern).format(date.withZoneSameInstant(ZoneOffset.UTC));
  }

  static DateTimeFormatter formatter(String dayjs) {
    StringBuilder java = new StringBuilder();
    StringBuilder literal = new StringBuilder();
    int i = 0;
    while (i < dayjs.length()) {
      char c = dayjs.charAt(i);
      if (c == '[') {
        int end = dayjs.indexOf(']', i);
        if (end < 0) end = dayjs.length();
        literal.append(dayjs, i + 1, end);
        i = end + 1;
        continue;
      }
      String token = null;
      for (String t : TOKENS) {
        if (dayjs.startsWith(t, i)) {
          token = t;
          break;
        }
      }
      if (token == null) {
        literal.append(c);
        i++;
        continue;
      }
      flush(java, literal);
      java.append(JAVA.get(token));
      i += token.length();
    }
    flush(java, literal);
    return DateTimeFormatter.ofPattern(java.toString(), Locale.ENGLISH);
  }

  private static void flush(StringBuilder java, StringBuilder literal) {
    if (literal.isEmpty()) return;
    java.append('\'').append(literal.toString().replace("'", "''")).append('\'');
    literal.setLength(0);
  }

  static ZonedDateTime add(ZonedDateTime date, Object duration) {
    ZonedDateTime out = date;
    for (Map.Entry<ChronoUnit, Long> part : duration(duration).entrySet()) out = out.plus(part.getValue(), part.getKey());
    return out;
  }

  static Map<ChronoUnit, Long> duration(Object duration) {
    Map<ChronoUnit, Long> parts = new LinkedHashMap<>();
    if (duration instanceof Map<?, ?> map) {
      map.forEach((k, v) -> parts.merge(unit(k.toString()), amount(v), Long::sum));
    } else if (duration != null) {
      Matcher m = DURATION_PART.matcher(duration.toString());
      while (m.find()) parts.merge(unit(m.group(1)), amount(m.group(2)), Long::sum);
    }
    if (parts.isEmpty()) throw new IllegalArgumentException("Give the time to add like '{days:3}' or (object days=3)");
    return parts;
  }

  private static long amount(Object value) {
    long n;
    try {
      n = value instanceof Number num ? num.longValue() : Long.parseLong(value.toString().trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("'" + value + "' isn't a whole number");
    }
    if (Math.abs(n) > MAX_AMOUNT) throw new IllegalArgumentException("Add at most " + MAX_AMOUNT + " of a unit at a time");
    return n;
  }

  static ChronoUnit unit(String name) {
    String key = name.trim().toLowerCase(Locale.ROOT);
    if (key.endsWith("s")) key = key.substring(0, key.length() - 1);
    ChronoUnit unit = UNITS.get(key);
    if (unit == null) throw new IllegalArgumentException("Unknown unit '" + name + "'; use milliseconds, seconds, minutes, hours, days, weeks, months or years");
    return unit;
  }

  static long diff(ZonedDateTime start, ZonedDateTime end, String unit) {
    return unit(unit).between(start, end);
  }
}
