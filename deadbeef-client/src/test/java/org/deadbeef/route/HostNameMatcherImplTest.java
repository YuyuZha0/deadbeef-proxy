package org.deadbeef.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@code match} is synchronous, so assertions are direct. {@link RunTestOnContext} only manages the
 * {@link Vertx} lifecycle (the matcher needs one for its close hook).
 */
@RunWith(VertxUnitRunner.class)
public class HostNameMatcherImplTest {

  @Rule public RunTestOnContext rule = new RunTestOnContext();

  private Vertx vertx() {
    return rule.vertx();
  }

  // ---- fnMatchToRegex: pure translation, cross-checked against java.util.Pattern ----

  @Test
  public void exactPatternEscapesDots() {
    String regex = HostNameMatcherImpl.fnMatchToRegex("a.b.com");
    assertEquals("^a\\.b\\.com$", regex);
    assertTrue(Pattern.compile(regex).matcher("a.b.com").matches());
    assertFalse(Pattern.compile(regex).matcher("axbxcom").matches());
  }

  @Test
  public void starBecomesDotStar() {
    String regex = HostNameMatcherImpl.fnMatchToRegex("*.example.com");
    assertEquals("^.*\\.example\\.com$", regex);
    Pattern p = Pattern.compile(regex);
    assertTrue(p.matcher("a.example.com").matches());
    assertTrue(p.matcher("a.b.example.com").matches());
    assertFalse(p.matcher("example.com").matches());
    assertFalse(p.matcher("xexample.com").matches());
  }

  @Test
  public void questionBecomesSingleChar() {
    String regex = HostNameMatcherImpl.fnMatchToRegex("a?c.test");
    assertEquals("^a.c\\.test$", regex);
    Pattern p = Pattern.compile(regex);
    assertTrue(p.matcher("abc.test").matches());
    assertFalse(p.matcher("ac.test").matches());
    assertFalse(p.matcher("abbc.test").matches());
  }

  @Test
  public void escapesRegexMetacharacters() {
    String regex = HostNameMatcherImpl.fnMatchToRegex("a+(b).c");
    assertEquals("^a\\+\\(b\\)\\.c$", regex);
    assertTrue(Pattern.compile(regex).matcher("a+(b).c").matches());
  }

  // ---- match: behaviour against the Hyperscan engine (synchronous) ----

  @Test
  public void matchesWildcardSubdomainsButNotApexOrGluedSuffix() {
    HostNameMatcher matcher = HostNameMatcher.create(vertx(), List.of("*.example.com"));
    assertTrue(matcher.match("a.example.com"));
    assertTrue(matcher.match("a.b.example.com"));
    assertFalse(matcher.match("example.com"));
    assertFalse(matcher.match("xexample.com")); // guards against PREFILTER false positives
  }

  @Test
  public void matchingIsCaseInsensitive() {
    HostNameMatcher matcher = HostNameMatcher.create(vertx(), List.of("*.Example.COM"));
    assertTrue(matcher.match("API.Example.com"));
  }

  @Test
  public void exactHostNameMatch() {
    HostNameMatcher matcher = HostNameMatcher.create(vertx(), List.of("api.test"));
    assertTrue(matcher.match("api.test"));
    assertFalse(matcher.match("api.test.evil.com"));
    assertFalse(matcher.match("xapi.test"));
  }

  @Test
  public void emptyHostNameIsFalse() {
    HostNameMatcher matcher = HostNameMatcher.create(vertx(), List.of("*.example.com"));
    assertFalse(matcher.match(""));
  }

  @Test
  public void exactIpMatch() {
    HostNameMatcher matcher = HostNameMatcher.create(vertx(), List.of("127.0.0.1", "::1"));
    assertTrue(matcher.match("127.0.0.1"));
    assertTrue(matcher.match("::1"));
    assertFalse(matcher.match("127.0.0.2"));
  }

  @Test
  public void ipMatchIsCanonicalAcrossSpellings() {
    HostNameMatcher matcher = HostNameMatcher.create(vertx(), List.of("::1"));
    assertTrue(matcher.match("0:0:0:0:0:0:0:1")); // expanded form of ::1
  }

  @Test
  public void ipOnlyListConstructsAndRejectsHostNames() {
    // No patterns -> must not crash compiling an empty Hyperscan database, and host names miss.
    HostNameMatcher matcher = HostNameMatcher.create(vertx(), List.of("10.0.0.1", "192.168.1.1"));
    assertTrue(matcher.match("10.0.0.1"));
    assertFalse(matcher.match("example.com"));
  }

  @Test
  public void closedMatcherThrows() {
    HostNameMatcherImpl matcher = HostNameMatcherImpl.create(vertx(), List.of("*.example.com"));
    Promise<Void> firstClose = Promise.promise();
    matcher.close(firstClose);
    assertTrue(firstClose.future().succeeded());

    assertThrows(IllegalStateException.class, () -> matcher.match("a.example.com"));

    Promise<Void> secondClose = Promise.promise(); // idempotent
    matcher.close(secondClose);
    assertTrue(secondClose.future().succeeded());
  }

  @Test
  public void emptyListYieldsEmptyMatcher() {
    HostNameMatcher matcher = HostNameMatcher.create(vertx(), List.of());
    assertSame(EmptyMatcher.INSTANCE, matcher);
    assertFalse(matcher.match("anything.com"));
  }
}
