package org.deadbeaf.protocol;

import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Maps;
import com.google.protobuf.Descriptors;
import io.vertx.core.MultiMap;
import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class HttpHeaderEncoder implements Function<MultiMap, HttpProto.Headers> {

  private static final Map<String, Descriptors.FieldDescriptor> FIELD_DESCRIPTOR_MAP;

  static {
    List<Descriptors.FieldDescriptor> fieldDescriptors =
        HttpProto.Headers.getDescriptor().getFields();
    Map<String, Descriptors.FieldDescriptor> map =
        Maps.newHashMapWithExpectedSize(fieldDescriptors.size());
    for (Descriptors.FieldDescriptor descriptor : fieldDescriptors) {
      if (descriptor.getJavaType() == Descriptors.FieldDescriptor.JavaType.STRING) {
        String name = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, descriptor.getName());
        map.put(name, descriptor);
      }
    }
    FIELD_DESCRIPTOR_MAP = map;
  }

  @Override
  public HttpProto.Headers apply(@NonNull MultiMap multiMap) {
    HttpProto.Headers.Builder builder = HttpProto.Headers.newBuilder();
    for (Map.Entry<String, String> entry : multiMap) {
      String key = Ascii.toLowerCase(entry.getKey());
      Descriptors.FieldDescriptor descriptor = FIELD_DESCRIPTOR_MAP.get(key);
      if (descriptor != null) {
        builder.setField(descriptor, entry.getValue());
      } else {
        builder.putUndeclaredPairs(key, entry.getValue());
      }
    }
    return builder.build();
  }
}
