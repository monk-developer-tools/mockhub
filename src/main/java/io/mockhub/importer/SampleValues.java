package io.mockhub.importer;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.datafaker.Faker;

final class SampleValues {
  static final int ARRAY_ITEMS = 3;

  private final Faker faker;
  private final Random random;

  SampleValues(long seed) {
    this.random = new Random(seed);
    this.faker = new Faker(Locale.ENGLISH, random);
  }

  String text(String name, String format) {
    String key = normalise(name);
    switch (format) {
      case "date":
        return date().toString();
      case "date-time":
        return date().atTime(9 + random.nextInt(9), random.nextInt(60)).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
      case "uuid":
        return new UUID(random.nextLong(), random.nextLong()).toString();
      case "email":
        return faker.internet().emailAddress();
      case "uri", "url":
        return "https://" + faker.internet().domainName() + "/" + faker.lorem().word();
      case "ipv4":
        return faker.internet().ipV4Address();
      case "hostname":
        return faker.internet().domainName();
      case "password":
        return "s3cret-" + random.nextInt(1000);
      default:
        break;
    }
    if (key.endsWith("email")) return faker.internet().emailAddress();
    if (key.endsWith("phone") || key.endsWith("phonenumber") || key.equals("mobile")) return faker.phoneNumber().phoneNumber();
    if (key.equals("firstname") || key.equals("givenname")) return faker.name().firstName();
    if (key.equals("lastname") || key.equals("surname") || key.equals("familyname")) return faker.name().lastName();
    if (key.equals("username") || key.equals("login") || key.equals("handle")) return faker.internet().username();
    if (key.equals("name") || key.equals("fullname") || key.equals("displayname") || key.equals("customername")) return faker.name().fullName();
    if (key.endsWith("company") || key.endsWith("companyname") || key.equals("organization") || key.equals("organisation")) return faker.company().name();
    if (key.equals("city") || key.equals("town")) return faker.address().city();
    if (key.equals("country")) return faker.address().country();
    if (key.equals("countrycode")) return faker.address().countryCode();
    if (key.equals("state") || key.equals("region") || key.equals("province")) return faker.address().state();
    if (key.contains("street") || key.equals("address") || key.equals("addressline1")) return faker.address().streetAddress();
    if (key.equals("zip") || key.equals("zipcode") || key.equals("postcode") || key.equals("postalcode")) return faker.address().zipCode();
    if (key.endsWith("url") || key.endsWith("website") || key.endsWith("link")) return "https://" + faker.internet().domainName();
    if (key.endsWith("image") || key.endsWith("avatar") || key.endsWith("photo")) return "https://picsum.photos/seed/" + random.nextInt(1000) + "/400/300";
    if (key.equals("title") || key.endsWith("productname") || key.equals("product")) return faker.commerce().productName();
    if (key.equals("description") || key.equals("summary") || key.equals("bio") || key.equals("notes") || key.equals("comment")) return faker.lorem().sentence();
    if (key.equals("currency") || key.equals("currencycode")) return faker.money().currencyCode();
    if (key.equals("color") || key.equals("colour")) return faker.color().name();
    if (key.endsWith("date") || key.endsWith("time") || key.endsWith("timestamp")
        || key.matches("(created|updated|deleted|modified|expires|issued|starts|ends|paid|shipped)at")) {
      return date().toString();
    }
    if (key.equals("id") || key.endsWith("id")) return new UUID(random.nextLong(), random.nextLong()).toString();
    if (key.equals("status")) return "active";
    if (key.equals("type") || key.equals("category")) return faker.commerce().department().toLowerCase(Locale.ROOT);
    if (key.equals("tag") || key.equals("code") || key.equals("slug") || key.equals("sku")) return faker.lorem().word() + "-" + (100 + random.nextInt(900));
    return faker.lorem().word();
  }

  long integer(String name) {
    String key = normalise(name);
    if (key.equals("id") || key.endsWith("id")) return 1 + random.nextInt(9999);
    if (key.contains("age")) return 18 + random.nextInt(60);
    if (key.contains("year")) return 2015 + random.nextInt(11);
    if (key.contains("quantity") || key.contains("count") || key.contains("qty")) return 1 + random.nextInt(9);
    if (key.contains("price") || key.contains("amount") || key.contains("total") || key.contains("cents")) return 100 + random.nextInt(9900);
    return 1 + random.nextInt(100);
  }

  double number(String name) {
    String key = normalise(name);
    if (key.contains("lat")) return Math.round((random.nextDouble() * 180 - 90) * 1e5) / 1e5;
    if (key.contains("lon") || key.contains("lng")) return Math.round((random.nextDouble() * 360 - 180) * 1e5) / 1e5;
    if (key.contains("rate") || key.contains("percent") || key.contains("ratio")) return Math.round(random.nextDouble() * 1000) / 10.0;
    return Math.round((5 + random.nextDouble() * 495) * 100) / 100.0;
  }

  boolean bool() {
    return random.nextInt(4) != 0;
  }

  private LocalDate date() {
    return LocalDate.of(2026, 1, 1).plusDays(random.nextInt(300));
  }

  private static String normalise(String name) {
    return name == null ? "" : name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
  }
}
