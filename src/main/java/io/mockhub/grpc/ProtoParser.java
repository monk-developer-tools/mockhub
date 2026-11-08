package io.mockhub.grpc;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumOptions;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProtoParser {
  public static final int MAX_CHARS = 256 * 1024;
  static final int MAX_DEPTH = 32;
  static final int MAX_DEFINITIONS = 2_000;
  private static final Pattern PREFIX = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
  private static final Map<String, Type> SCALARS = Map.ofEntries(
      Map.entry("double", Type.TYPE_DOUBLE), Map.entry("float", Type.TYPE_FLOAT), Map.entry("int64", Type.TYPE_INT64),
      Map.entry("uint64", Type.TYPE_UINT64), Map.entry("int32", Type.TYPE_INT32), Map.entry("fixed64", Type.TYPE_FIXED64),
      Map.entry("fixed32", Type.TYPE_FIXED32), Map.entry("bool", Type.TYPE_BOOL), Map.entry("string", Type.TYPE_STRING),
      Map.entry("bytes", Type.TYPE_BYTES), Map.entry("uint32", Type.TYPE_UINT32), Map.entry("sfixed32", Type.TYPE_SFIXED32),
      Map.entry("sfixed64", Type.TYPE_SFIXED64), Map.entry("sint32", Type.TYPE_SINT32), Map.entry("sint64", Type.TYPE_SINT64));
  private static final Set<String> MAP_KEYS = Set.of("int64", "uint64", "int32", "fixed64", "fixed32", "bool", "string",
      "uint32", "sfixed32", "sfixed64", "sint32", "sint64");

  private enum Kind { MESSAGE, ENUM }

  private static final class Field {
    String name;
    int number;
    Label label = Label.LABEL_OPTIONAL;
    String type;
    String scope;
    int line;
    int oneof = -1;
    boolean proto3Optional;
    String defaultValue;
    String jsonName;
  }

  private static final class Message {
    String name;
    boolean mapEntry;
    final List<Field> fields = new ArrayList<>();
    final List<Message> nested = new ArrayList<>();
    final List<EnumDescriptorProto> enums = new ArrayList<>();
    final List<String> oneofs = new ArrayList<>();
  }

  private record Rpc(String name, String input, String output, boolean clientStreaming, boolean serverStreaming, String scope, int line) {}

  private record Service(String name, List<Rpc> rpcs) {}

  private final ProtoTokens tokens;
  private final String prefix;
  private final List<Message> messages = new ArrayList<>();
  private final List<EnumDescriptorProto> enums = new ArrayList<>();
  private final List<Service> services = new ArrayList<>();
  private final Map<String, Kind> symbols = new HashMap<>();
  private final Set<String> scopes = new HashSet<>();
  private final List<String> imports = new ArrayList<>();
  private final Set<String> publicImports = new HashSet<>();
  private String packageName = "";
  private boolean proto3 = true;
  private int definitions;

  private ProtoParser(String text, String prefix) {
    this.tokens = new ProtoTokens(text);
    this.prefix = prefix;
  }

  public static FileDescriptorProto parse(String text, String fileName, String packagePrefix) {
    if (text == null || text.isBlank()) throw new GrpcException("The .proto file is empty");
    if (text.length() > MAX_CHARS) throw new GrpcException("A .proto file can be at most 256 KB");
    String prefix = packagePrefix == null ? "" : packagePrefix;
    if (!prefix.isEmpty() && !PREFIX.matcher(prefix).matches()) throw new IllegalArgumentException("Bad package prefix " + prefix);
    ProtoParser parser = new ProtoParser(text, prefix);
    parser.parseFile();
    return parser.build(prefix.isEmpty() ? fileName : prefix.replace('.', '/') + "/" + fileName);
  }

  private void parseFile() {
    if (tokens.peek().is("edition")) throw tokens.error(tokens.peek(), "Protobuf editions aren't supported; use syntax = \"proto3\" or \"proto2\"");
    if (tokens.accept("syntax")) {
      tokens.expect("=");
      String syntax = tokens.string();
      if (!syntax.equals("proto3") && !syntax.equals("proto2")) throw new GrpcException("Unknown syntax \"" + syntax + "\"");
      proto3 = syntax.equals("proto3");
      tokens.expect(";");
    } else {
      proto3 = false;
    }
    boolean packageSeen = false;
    boolean bodyStarted = false;
    String fullPackage = prefix;
    registerScopes(fullPackage);
    while (tokens.peek().kind() != ProtoTokens.Kind.END) {
      ProtoTokens.Token t = tokens.peek();
      if (tokens.accept(";")) continue;
      if (t.is("package")) {
        tokens.next();
        if (packageSeen) throw tokens.error(t, "A file can have only one package");
        if (bodyStarted) throw tokens.error(t, "Put the package line before the first message, enum or service");
        packageSeen = true;
        packageName = tokens.dottedName();
        if (packageName.startsWith(".")) throw tokens.error(t, "A package name can't start with a dot");
        tokens.expect(";");
        fullPackage = join(prefix, packageName);
        registerScopes(fullPackage);
      } else if (t.is("import")) {
        tokens.next();
        boolean isPublic = tokens.accept("public");
        if (!isPublic) tokens.accept("weak");
        String imported = tokens.string();
        imports.add(imported);
        if (isPublic) publicImports.add(imported);
        tokens.expect(";");
      } else if (t.is("option")) {
        tokens.next();
        skipOption();
      } else if (t.is("message")) {
        tokens.next();
        bodyStarted = true;
        messages.add(message(fullPackage, 1));
      } else if (t.is("enum")) {
        tokens.next();
        bodyStarted = true;
        enums.add(enumType(fullPackage));
      } else if (t.is("service")) {
        tokens.next();
        bodyStarted = true;
        services.add(service(fullPackage));
      } else if (t.is("extend")) {
        throw tokens.error(t, "extend isn't supported; leave custom option definitions out of the file");
      } else {
        throw tokens.error(t, "Expected message, enum, service, import, option or package but found " + ProtoTokens.describe(t));
      }
    }
  }

  private Message message(String scope, int depth) {
    if (depth > MAX_DEPTH) throw tokens.error(tokens.peek(), "Messages are nested too deeply");
    count();
    Message message = new Message();
    message.name = tokens.word();
    String full = join(scope, message.name);
    define(full, Kind.MESSAGE);
    tokens.expect("{");
    while (!tokens.accept("}")) {
      ProtoTokens.Token t = tokens.peek();
      if (t.kind() == ProtoTokens.Kind.END) throw tokens.error(t, "Message " + message.name + " is never closed with }");
      if (tokens.accept(";")) continue;
      if (t.is("message")) {
        tokens.next();
        message.nested.add(message(full, depth + 1));
      } else if (t.is("enum")) {
        tokens.next();
        message.enums.add(enumType(full));
      } else if (t.is("oneof")) {
        tokens.next();
        oneof(message, full);
      } else if (t.is("option")) {
        tokens.next();
        skipOption();
      } else if (t.is("reserved") || t.is("extensions")) {
        skipStatement();
      } else if (t.is("extend")) {
        throw tokens.error(t, "extend isn't supported");
      } else if (t.is("map") && tokens.peek(1).is("<")) {
        tokens.next();
        mapField(message, full);
      } else if (t.is("group") || (tokens.peek(1).is("group") && (t.is("optional") || t.is("required") || t.is("repeated")))) {
        throw tokens.error(t, "Groups aren't supported");
      } else {
        message.fields.add(field(full, false));
      }
    }
    return message;
  }

  private Field field(String scope, boolean inOneof) {
    ProtoTokens.Token start = tokens.peek();
    count();
    Field field = new Field();
    field.scope = scope;
    field.line = start.line();
    if (tokens.accept("repeated")) {
      field.label = Label.LABEL_REPEATED;
    } else if (tokens.accept("required")) {
      if (proto3) throw tokens.error(start, "required fields aren't allowed in proto3");
      field.label = Label.LABEL_REQUIRED;
    } else if (tokens.accept("optional")) {
      field.proto3Optional = proto3;
    }
    if (inOneof && (field.label != Label.LABEL_OPTIONAL || field.proto3Optional)) throw tokens.error(start, "Fields in a oneof can't have a label");
    field.type = tokens.dottedName();
    field.name = tokens.word();
    tokens.expect("=");
    field.number = tokens.integer();
    fieldOptions(field);
    tokens.expect(";");
    return field;
  }

  private void fieldOptions(Field field) {
    if (!tokens.accept("[")) return;
    do {
      ProtoTokens.Token at = tokens.peek();
      String name = optionName();
      tokens.expect("=");
      if (name.equals("default")) {
        field.defaultValue = constant();
      } else if (name.equals("json_name")) {
        field.jsonName = tokens.string();
      } else {
        skipValue(at);
      }
    } while (tokens.accept(","));
    tokens.expect("]");
  }

  private String constant() {
    if (tokens.peek().kind() == ProtoTokens.Kind.STRING) return tokens.string();
    StringBuilder value = new StringBuilder();
    if (tokens.accept("-")) value.append('-');
    else tokens.accept("+");
    ProtoTokens.Token v = tokens.next();
    if (v.kind() != ProtoTokens.Kind.NUMBER && v.kind() != ProtoTokens.Kind.WORD) throw tokens.error(v, "Expected a value but found " + ProtoTokens.describe(v));
    return value.append(v.text()).toString();
  }

  private void mapField(Message message, String scope) {
    ProtoTokens.Token start = tokens.peek();
    count();
    tokens.expect("<");
    String key = tokens.dottedName();
    if (!MAP_KEYS.contains(key)) throw tokens.error(start, "A map key must be an integer, bool or string type, not " + key);
    tokens.expect(",");
    String value = tokens.dottedName();
    tokens.expect(">");
    Field field = new Field();
    field.name = tokens.word();
    tokens.expect("=");
    field.number = tokens.integer();
    fieldOptions(field);
    tokens.expect(";");
    Message entry = new Message();
    entry.name = camel(field.name) + "Entry";
    entry.mapEntry = true;
    String entryFull = join(scope, entry.name);
    define(entryFull, Kind.MESSAGE);
    entry.fields.add(simple("key", 1, key, entryFull, start.line()));
    entry.fields.add(simple("value", 2, value, entryFull, start.line()));
    message.nested.add(entry);
    field.label = Label.LABEL_REPEATED;
    field.type = "." + entryFull;
    field.scope = scope;
    field.line = start.line();
    message.fields.add(field);
  }

  private static Field simple(String name, int number, String type, String scope, int line) {
    Field f = new Field();
    f.name = name;
    f.number = number;
    f.type = type;
    f.scope = scope;
    f.line = line;
    return f;
  }

  private void oneof(Message message, String scope) {
    String name = tokens.word();
    int index = message.oneofs.size();
    message.oneofs.add(name);
    tokens.expect("{");
    while (!tokens.accept("}")) {
      ProtoTokens.Token t = tokens.peek();
      if (t.kind() == ProtoTokens.Kind.END) throw tokens.error(t, "oneof " + name + " is never closed with }");
      if (tokens.accept(";")) continue;
      if (t.is("option")) {
        tokens.next();
        skipOption();
        continue;
      }
      if (t.is("map") && tokens.peek(1).is("<")) throw tokens.error(t, "A map can't be in a oneof");
      Field field = field(scope, true);
      field.oneof = index;
      message.fields.add(field);
    }
  }

  private EnumDescriptorProto enumType(String scope) {
    count();
    String name = tokens.word();
    define(join(scope, name), Kind.ENUM);
    EnumDescriptorProto.Builder e = EnumDescriptorProto.newBuilder().setName(name);
    tokens.expect("{");
    while (!tokens.accept("}")) {
      ProtoTokens.Token t = tokens.peek();
      if (t.kind() == ProtoTokens.Kind.END) throw tokens.error(t, "Enum " + name + " is never closed with }");
      if (tokens.accept(";")) continue;
      if (t.is("option")) {
        tokens.next();
        ProtoTokens.Token at = tokens.peek();
        String option = optionName();
        tokens.expect("=");
        if (option.equals("allow_alias")) e.setOptions(EnumOptions.newBuilder().setAllowAlias(tokens.word().equals("true")));
        else skipValue(at);
        tokens.expect(";");
      } else if (t.is("reserved")) {
        skipStatement();
      } else {
        count();
        String value = tokens.word();
        tokens.expect("=");
        int number = tokens.integer();
        if (tokens.accept("[")) {
          do {
            ProtoTokens.Token at = tokens.peek();
            optionName();
            tokens.expect("=");
            skipValue(at);
          } while (tokens.accept(","));
          tokens.expect("]");
        }
        tokens.expect(";");
        e.addValue(EnumValueDescriptorProto.newBuilder().setName(value).setNumber(number));
      }
    }
    if (e.getValueCount() == 0) throw new GrpcException("Enum " + name + " has no values");
    if (proto3 && e.getValue(0).getNumber() != 0) throw new GrpcException("The first value of enum " + name + " must be 0 in proto3");
    return e.build();
  }

  private Service service(String scope) {
    count();
    Service service = new Service(tokens.word(), new ArrayList<>());
    tokens.expect("{");
    while (!tokens.accept("}")) {
      ProtoTokens.Token t = tokens.peek();
      if (t.kind() == ProtoTokens.Kind.END) throw tokens.error(t, "Service " + service.name() + " is never closed with }");
      if (tokens.accept(";")) continue;
      if (t.is("option")) {
        tokens.next();
        skipOption();
        continue;
      }
      tokens.expect("rpc");
      count();
      String name = tokens.word();
      tokens.expect("(");
      boolean clientStreaming = streamKeyword();
      String input = tokens.dottedName();
      tokens.expect(")");
      tokens.expect("returns");
      tokens.expect("(");
      boolean serverStreaming = streamKeyword();
      String output = tokens.dottedName();
      tokens.expect(")");
      if (tokens.accept("{")) {
        while (!tokens.accept("}")) {
          ProtoTokens.Token inner = tokens.peek();
          if (inner.kind() == ProtoTokens.Kind.END) throw tokens.error(inner, "rpc " + name + " is never closed with }");
          if (tokens.accept(";")) continue;
          tokens.expect("option");
          skipOption();
        }
      } else {
        tokens.expect(";");
      }
      service.rpcs().add(new Rpc(name, input, output, clientStreaming, serverStreaming, scope, t.line()));
    }
    return service;
  }

  private boolean streamKeyword() {
    if (tokens.peek().is("stream") && !tokens.peek(1).is(")") && !tokens.peek(1).is(".")) {
      tokens.next();
      return true;
    }
    return false;
  }

  private String optionName() {
    StringBuilder name = new StringBuilder();
    if (tokens.accept("(")) {
      name.append('(').append(tokens.dottedName()).append(')');
      tokens.expect(")");
    } else {
      name.append(tokens.word());
    }
    while (tokens.accept(".")) {
      if (tokens.accept("(")) {
        name.append(".(").append(tokens.dottedName()).append(')');
        tokens.expect(")");
      } else {
        name.append('.').append(tokens.word());
      }
    }
    return name.toString();
  }

  private void skipOption() {
    ProtoTokens.Token at = tokens.peek();
    optionName();
    tokens.expect("=");
    skipValue(at);
    tokens.expect(";");
  }

  private void skipValue(ProtoTokens.Token at) {
    if (tokens.peek().is("{")) {
      int depth = 0;
      do {
        ProtoTokens.Token t = tokens.next();
        if (t.kind() == ProtoTokens.Kind.END) throw tokens.error(at, "An option value is never closed with }");
        if (t.is("{")) depth++;
        if (t.is("}")) depth--;
      } while (depth > 0);
      return;
    }
    if (tokens.peek().kind() == ProtoTokens.Kind.STRING) {
      tokens.string();
      return;
    }
    if (!tokens.accept("-")) tokens.accept("+");
    ProtoTokens.Token v = tokens.next();
    if (v.kind() == ProtoTokens.Kind.WORD) {
      while (tokens.peek().is(".") && tokens.peek(1).kind() == ProtoTokens.Kind.WORD) {
        tokens.next();
        tokens.next();
      }
      return;
    }
    if (v.kind() != ProtoTokens.Kind.NUMBER) throw tokens.error(v, "Expected an option value but found " + ProtoTokens.describe(v));
  }

  private void skipStatement() {
    ProtoTokens.Token start = tokens.next();
    while (!tokens.accept(";")) {
      if (tokens.peek().kind() == ProtoTokens.Kind.END) throw tokens.error(start, start.text() + " is never ended with ;");
      tokens.next();
    }
  }

  private void count() {
    if (++definitions > MAX_DEFINITIONS) throw new GrpcException("The .proto file defines too many things; split it up");
  }

  private void define(String full, Kind kind) {
    if (symbols.containsKey(full)) throw new GrpcException(full + " is defined twice");
    symbols.put(full, kind);
    registerScopes(full);
  }

  private void registerScopes(String full) {
    if (full == null || full.isEmpty()) return;
    StringBuilder name = new StringBuilder();
    for (String part : full.split("\\.")) {
      if (name.length() > 0) name.append('.');
      name.append(part);
      scopes.add(name.toString());
    }
  }

  private FileDescriptorProto build(String fileName) {
    FileDescriptorProto.Builder file = FileDescriptorProto.newBuilder().setName(fileName);
    String fullPackage = join(prefix, packageName);
    if (!fullPackage.isEmpty()) file.setPackage(fullPackage);
    if (proto3) file.setSyntax("proto3");
    for (String dependency : imports) {
      FileDescriptor known = ProtoFiles.wellKnown(dependency);
      if (known == null || file.getDependencyList().contains(dependency)) continue;
      file.addDependency(dependency);
      if (publicImports.contains(dependency)) file.addPublicDependency(file.getDependencyCount() - 1);
      for (Descriptor message : known.getMessageTypes()) addKnown(message);
      for (EnumDescriptor e : known.getEnumTypes()) symbols.put(e.getFullName(), Kind.ENUM);
      registerScopes(known.getPackage());
    }
    for (Message m : messages) file.addMessageType(build(m));
    file.addAllEnumType(enums);
    for (Service s : services) {
      ServiceDescriptorProto.Builder service = ServiceDescriptorProto.newBuilder().setName(s.name());
      for (Rpc rpc : s.rpcs()) {
        MethodDescriptorProto.Builder method = MethodDescriptorProto.newBuilder().setName(rpc.name())
            .setInputType("." + messageType(rpc.input(), rpc.scope(), rpc.line()))
            .setOutputType("." + messageType(rpc.output(), rpc.scope(), rpc.line()));
        if (rpc.clientStreaming()) method.setClientStreaming(true);
        if (rpc.serverStreaming()) method.setServerStreaming(true);
        service.addMethod(method);
      }
      file.addService(service);
    }
    return file.build();
  }

  private DescriptorProto build(Message m) {
    DescriptorProto.Builder out = DescriptorProto.newBuilder().setName(m.name);
    if (m.mapEntry) out.setOptions(MessageOptions.newBuilder().setMapEntry(true));
    m.oneofs.forEach(name -> out.addOneofDecl(OneofDescriptorProto.newBuilder().setName(name)));
    Set<String> taken = new HashSet<>(m.oneofs);
    m.fields.forEach(f -> taken.add(f.name));
    for (Field f : m.fields) {
      if (!f.proto3Optional) continue;
      String name = "_" + f.name;
      while (taken.contains(name)) name = "X" + name;
      taken.add(name);
      f.oneof = out.getOneofDeclCount();
      out.addOneofDecl(OneofDescriptorProto.newBuilder().setName(name));
    }
    for (Field f : m.fields) out.addField(build(f));
    for (Message nested : m.nested) out.addNestedType(build(nested));
    out.addAllEnumType(m.enums);
    return out.build();
  }

  private FieldDescriptorProto build(Field f) {
    FieldDescriptorProto.Builder out = FieldDescriptorProto.newBuilder().setName(f.name).setNumber(f.number).setLabel(f.label);
    Type scalar = SCALARS.get(f.type);
    if (scalar != null) {
      out.setType(scalar);
    } else {
      String full = lookup(f.type, f.scope, f.line);
      out.setTypeName("." + full).setType(symbols.get(full) == Kind.ENUM ? Type.TYPE_ENUM : Type.TYPE_MESSAGE);
    }
    if (f.oneof >= 0) out.setOneofIndex(f.oneof);
    if (f.proto3Optional) out.setProto3Optional(true);
    if (f.defaultValue != null) out.setDefaultValue(f.defaultValue);
    if (f.jsonName != null) out.setJsonName(f.jsonName);
    return out.build();
  }

  private String messageType(String name, String scope, int line) {
    String full = lookup(name, scope, line);
    if (symbols.get(full) != Kind.MESSAGE) throw new GrpcException("Line " + line + ": " + name + " is an enum; an rpc takes and returns messages");
    return full;
  }

  private void addKnown(Descriptor message) {
    symbols.put(message.getFullName(), Kind.MESSAGE);
    for (Descriptor nested : message.getNestedTypes()) addKnown(nested);
    for (EnumDescriptor e : message.getEnumTypes()) symbols.put(e.getFullName(), Kind.ENUM);
  }

  private String lookup(String name, String scope, int line) {
    if (name.startsWith(".")) {
      String absolute = name.substring(1);
      if (symbols.containsKey(absolute)) return absolute;
      String prefixed = join(prefix, absolute);
      if (!prefix.isEmpty() && symbols.containsKey(prefixed)) return prefixed;
      throw unknown(name, line);
    }
    String first = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
    String current = scope;
    while (true) {
      String candidate = join(current, first);
      if (symbols.containsKey(candidate) || scopes.contains(candidate)) {
        String full = join(current, name);
        if (symbols.containsKey(full)) return full;
      }
      if (current.isEmpty()) break;
      int dot = current.lastIndexOf('.');
      current = dot < 0 ? "" : current.substring(0, dot);
    }
    throw unknown(name, line);
  }

  private GrpcException unknown(String name, int line) {
    return new GrpcException("Line " + line + ": Unknown type " + name
        + ". Define it in this file; only the google/protobuf types can be imported");
  }

  private static String join(String scope, String name) {
    if (scope == null || scope.isEmpty()) return name == null ? "" : name;
    if (name == null || name.isEmpty()) return scope;
    return scope + "." + name;
  }

  private static String camel(String name) {
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (char c : name.toCharArray()) {
      if (c == '_') {
        upper = true;
      } else {
        out.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return out.toString();
  }
}
