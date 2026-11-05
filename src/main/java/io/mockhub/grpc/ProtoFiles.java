package io.mockhub.grpc;

import com.google.protobuf.AnyProto;
import com.google.protobuf.ApiProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DurationProto;
import com.google.protobuf.EmptyProto;
import com.google.protobuf.FieldMaskProto;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.SourceContextProto;
import com.google.protobuf.StructProto;
import com.google.protobuf.TimestampProto;
import com.google.protobuf.TypeProto;
import com.google.protobuf.WrappersProto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ProtoFiles {
  public static final int MAX_BYTES = 4 * 1024 * 1024;

  private static final Map<String, FileDescriptor> WELL_KNOWN = new LinkedHashMap<>();

  static {
    Stream.of(AnyProto.getDescriptor(), ApiProto.getDescriptor(), DurationProto.getDescriptor(), EmptyProto.getDescriptor(),
            FieldMaskProto.getDescriptor(), SourceContextProto.getDescriptor(), StructProto.getDescriptor(),
            TimestampProto.getDescriptor(), TypeProto.getDescriptor(), WrappersProto.getDescriptor())
        .forEach(f -> WELL_KNOWN.put(f.getName(), f));
  }

  private ProtoFiles() {}

  public static List<FileDescriptor> parse(byte[] bytes) {
    if (bytes == null || bytes.length == 0) throw new GrpcException("The descriptor file is empty");
    if (bytes.length > MAX_BYTES) throw new GrpcException("Descriptor files can be at most 4 MB");
    FileDescriptorSet set;
    try {
      set = FileDescriptorSet.parseFrom(bytes);
    } catch (InvalidProtocolBufferException e) {
      throw new GrpcException("This is not a protobuf descriptor set. Create one with protoc --include_imports --descriptor_set_out");
    }
    if (set.getFileCount() == 0) throw new GrpcException("The descriptor set contains no .proto files");
    Map<String, FileDescriptorProto> protos = new LinkedHashMap<>();
    set.getFileList().forEach(p -> protos.putIfAbsent(p.getName(), p));
    Map<String, FileDescriptor> built = new LinkedHashMap<>();
    for (String name : protos.keySet()) build(name, protos, built, new ArrayList<>());
    return built.values().stream().filter(f -> protos.containsKey(f.getName())).toList();
  }

  private static FileDescriptor build(String name, Map<String, FileDescriptorProto> protos, Map<String, FileDescriptor> built, List<String> path) {
    FileDescriptor done = built.get(name);
    if (done != null) return done;
    FileDescriptorProto proto = protos.get(name);
    if (proto == null) {
      FileDescriptor known = WELL_KNOWN.get(name);
      if (known != null) return known;
      throw new GrpcException("The descriptor set is missing " + name + ". Run protoc with --include_imports");
    }
    if (path.contains(name)) throw new GrpcException("Circular import through " + name);
    path.add(name);
    List<FileDescriptor> deps = new ArrayList<>();
    for (String dep : proto.getDependencyList()) deps.add(build(dep, protos, built, path));
    path.remove(name);
    try {
      FileDescriptor file = FileDescriptor.buildFrom(proto, deps.toArray(FileDescriptor[]::new));
      built.put(name, file);
      return file;
    } catch (DescriptorValidationException e) {
      throw new GrpcException("Invalid " + name + ": " + e.getDescription());
    }
  }
}
