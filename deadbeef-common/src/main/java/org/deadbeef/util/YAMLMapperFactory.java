package org.deadbeef.util;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.util.function.Supplier;

public final class YAMLMapperFactory implements Supplier<YAMLMapper> {

  @Override
  public YAMLMapper get() {
    YAMLMapper yamlMapper = new YAMLMapper();
    yamlMapper.registerModule(new VertxJsonModule());
    return yamlMapper;
  }
}
