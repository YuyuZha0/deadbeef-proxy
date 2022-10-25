package org.deadbeef.util;

import com.google.common.base.Splitter;
import io.vertx.core.MultiMap;
import org.deadbeef.protocol.HttpProto;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class HttpHeaderEncoderTest {

  @Test
  public void test() {
    String text =
        "accept: */*\n"
            + "accept-encoding: gzip, deflate, br\n"
            + "accept-language: zh-CN,zh;q=0.9\n"
            + "if-modified-since: Sun, 07 Aug 2022 00:37:58 GMT\n"
            + "if-none-match: W/\"62ef0966-21aa\"\n"
            + "referer: https://google.github.io/styleguide/javaguide.html\n"
            + "sec-ch-ua: \"Google Chrome\";v=\"105\", \"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"105\"\n"
            + "sec-ch-ua-mobile: ?0\n"
            + "sec-ch-ua-platform: \"macOS\"\n"
            + "sec-fetch-dest: script\n"
            + "sec-fetch-mode: no-cors\n"
            + "sec-fetch-site: same-origin\n"
            + "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.0.0 Safari/537.36";
    MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
    for (String line : Splitter.on('\n').split(text)) {
      int split = line.indexOf(':');
      if (split > 0) {
        multiMap.add(line.substring(0, split), line.substring(split + 1).trim());
      }
    }
    System.out.println(multiMap);
    HttpHeaderEncoder encoder = new HttpHeaderEncoder();
    HttpProto.Headers headers = encoder.apply(multiMap);
    System.out.println(headers);
    assertEquals("gzip, deflate, br", headers.getAcceptEncoding());
    assertEquals("no-cors", headers.getUndeclaredPairsMap().get("sec-fetch-mode"));

    HttpHeaderDecoder decoder = new HttpHeaderDecoder();
    MultiMap multiMap1 = decoder.apply(headers);
    System.out.println(multiMap1);
    assertEquals(multiMap.size(), multiMap1.size());
    for (Map.Entry<String, String> entry : multiMap) {
      assertEquals(entry.getValue(), multiMap1.get(entry.getKey()));
    }
  }
}
