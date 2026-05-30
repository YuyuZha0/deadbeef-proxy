package org.deadbeef.route;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies the bundled routing lists load (comments/blanks skipped) and match as intended. */
@RunWith(VertxUnitRunner.class)
public class HostNameMatcherRulesTest {

  @Rule public RunTestOnContext rule = new RunTestOnContext();

  @Test
  public void localOnlyMatchesChinaMajorsNotForeign() {
    HostNameMatcher m = HostNameMatcher.fromClasspathFile(rule.vertx(), "local_only.txt");
    assertTrue(m.match("baidu.com"));
    assertTrue(m.match("www.baidu.com"));
    assertTrue(m.match("api.weibo.com"));
    assertTrue(m.match("data.gov.cn")); // *.gov.cn
    assertFalse(m.match("www.google.com"));
    assertFalse(m.match("youtube.com"));
  }

  @Test
  public void remoteOnlyMatchesBlockedNotChina() {
    HostNameMatcher m = HostNameMatcher.fromClasspathFile(rule.vertx(), "remote_only.txt");
    assertTrue(m.match("www.google.com"));
    assertTrue(m.match("youtube.com"));
    assertTrue(m.match("api.twitter.com"));
    assertTrue(m.match("i.ytimg.com"));
    assertFalse(m.match("www.baidu.com"));
    assertFalse(m.match("taobao.com"));
  }
}
