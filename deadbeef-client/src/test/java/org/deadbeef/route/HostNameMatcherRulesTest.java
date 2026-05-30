package org.deadbeef.route;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Verifies the bundled routing lists load (comments/blanks skipped) and match as intended. */
public class HostNameMatcherRulesTest {

  @Test
  public void localOnlyMatchesChinaMajorsNotForeign() {
    HostNameMatcher m = HostNameMatcher.fromClasspathFile("local_only.txt");
    assertTrue(m.match("baidu.com"));
    assertTrue(m.match("www.baidu.com"));
    assertTrue(m.match("api.weibo.com"));
    assertTrue(m.match("data.gov.cn")); // *.gov.cn
    assertFalse(m.match("www.google.com"));
    assertFalse(m.match("youtube.com"));
  }

  @Test
  public void inlineAndWholeLineCommentsAreStripped() {
    HostNameMatcher m = HostNameMatcher.fromClasspathFile("test_hosts.txt");
    assertTrue(m.match("foo.example.com")); // inline comment stripped
    assertTrue(m.match("bar.example.com"));
    assertTrue(m.match("baz.example.com")); // 'baz.example.com#tight' -> 'baz.example.com'
    // comment text must not have leaked into a pattern
    assertFalse(m.match("inline"));
    assertFalse(m.match("comment"));
  }

  @Test
  public void remoteOnlyMatchesBlockedNotChina() {
    HostNameMatcher m = HostNameMatcher.fromClasspathFile("remote_only.txt");
    assertTrue(m.match("www.google.com"));
    assertTrue(m.match("youtube.com"));
    assertTrue(m.match("api.twitter.com"));
    assertTrue(m.match("i.ytimg.com"));
    assertFalse(m.match("www.baidu.com"));
    assertFalse(m.match("taobao.com"));
  }
}
