package org.deadbeaf.protocol;

import com.google.common.base.Splitter;
import com.google.protobuf.Descriptors;
import io.vertx.core.MultiMap;
import lombok.NonNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HttpHeaderDecoder implements Function<HttpProto.Headers, MultiMap> {

  private static final List<Descriptors.FieldDescriptor> FIELD_DESCRIPTORS =
      HttpProto.Headers.getDescriptor().getFields().stream()
          .filter(
              fieldDescriptor ->
                  fieldDescriptor.getJavaType() == Descriptors.FieldDescriptor.JavaType.STRING)
          .collect(Collectors.toList());
  private final boolean allLowerCase;

  public HttpHeaderDecoder(boolean allLowerCase) {
    this.allLowerCase = allLowerCase;
  }

  public HttpHeaderDecoder() {
    this(false);
  }

  private String transformName(String fieldName) {
    Iterator<String> iterator = Splitter.on('_').split(fieldName).iterator();
    StringBuilder builder = new StringBuilder(fieldName.length());
    while (iterator.hasNext()) {
      String next = iterator.next();
      if (allLowerCase) {
        builder.append(next);
      } else {
        builder.append(Character.toUpperCase(next.charAt(0))).append(next.substring(1));
      }
      if (iterator.hasNext()) {
        builder.append('-');
      }
    }
    return builder.toString();
  }

  @Override
  public MultiMap apply(@NonNull HttpProto.Headers headers) {
    MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
    visit(headers, multiMap::add);
    return multiMap;
  }

  public void visit(
      @NonNull HttpProto.Headers headers,
      @NonNull BiConsumer<? super String, ? super String> consumer) {
    for (Descriptors.FieldDescriptor descriptor : FIELD_DESCRIPTORS) {
      if (headers.hasField(descriptor)) {
        consumer.accept(transformName(descriptor.getName()), (String) headers.getField(descriptor));
      }
    }
    if (headers.getUndeclaredPairsCount() > 0) {
      for (Map.Entry<String, String> entry : headers.getUndeclaredPairsMap().entrySet()) {
        consumer.accept(entry.getKey(), entry.getValue());
      }
    }
  }
}
