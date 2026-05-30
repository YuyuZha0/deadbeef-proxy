package org.deadbeef.route;

import com.gliwka.hyperscan.wrapper.Database;
import com.gliwka.hyperscan.wrapper.Expression;
import com.gliwka.hyperscan.wrapper.ExpressionFlag;
import com.gliwka.hyperscan.wrapper.Scanner;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;
import io.netty.util.concurrent.FastThreadLocal;
import io.vertx.core.Closeable;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class HostNameMatcherImpl implements HostNameMatcher, Closeable {

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final ReadWriteLock closeLock = new ReentrantReadWriteLock(false);
  private final Set<String> ipAddresses;
  // null when no host-name patterns were configured (IP-only list); the pattern branch of match()
  // then short-circuits to false.
  private final Database database;
  private final FastThreadLocal<Scanner> scanner =
      new FastThreadLocal<>() {
        @Override
        protected Scanner initialValue() {
          if (database == null) {
            return null; // No patterns, so no scanner needed
          }
          Scanner s = new Scanner();
          s.allocScratch(database);
          return s;
        }

        @Override
        protected void onRemoval(Scanner s) {
          closeQuietly(s);
        }
      };

  private static void closeQuietly(java.io.Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception e) {
        log.warn("Failed to close {}", closeable.getClass().getSimpleName(), e);
      }
    }
  }

  static HostNameMatcherImpl create(Vertx vertx, List<String> hostNames) {
    Set<String> ipAddresses = new HashSet<>();
    List<Expression> expressions = new ArrayList<>();
    for (String hostName : hostNames) {
      if (InetAddresses.isInetAddress(hostName)) {
        ipAddresses.add(canonicalIp(hostName));
      } else {
        expressions.add(mapToExpression(hostName));
      }
    }
    Database database = null;
    // Hyperscan rejects an empty expression set, so only build the engine when there are patterns.
    if (!expressions.isEmpty()) {
      try {
        database = Database.compile(expressions);
      } catch (Exception e) {
        closeQuietly(database);
        throw new RuntimeException("Failed to create HostNameMatcherImpl", e);
      }
    }
    VertxInternal vertxInternal = (VertxInternal) vertx;
    HostNameMatcherImpl matcher = new HostNameMatcherImpl(ipAddresses, database);
    vertxInternal.addCloseHook(matcher);
    return matcher;
  }

  /** Canonical text form of an IP literal so equivalent spellings compare equal. */
  private static String canonicalIp(String ip) {
    return InetAddresses.toAddrString(InetAddresses.forString(ip));
  }

  @VisibleForTesting
  static Expression mapToExpression(String hostNamePattern) {
    String regex = fnMatchToRegex(hostNamePattern);
    return new Expression(
        regex,
        EnumSet.of(
            ExpressionFlag.UTF8,
            ExpressionFlag.ALLOWEMPTY,
            ExpressionFlag.SINGLEMATCH,
            ExpressionFlag.CASELESS));
  }

  /**
   * Translate a glob ({@code *}, {@code ?}) host pattern into an anchored regex. Metacharacters are
   * escaped explicitly (no {@code \Q..\E}) so the result is valid for both {@code
   * java.util.Pattern} and Hyperscan.
   */
  @VisibleForTesting
  static String fnMatchToRegex(String pattern) {
    StringBuilder sb = new StringBuilder(pattern.length() + 8).append('^');
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      switch (c) {
        case '*' -> sb.append(".*");
        case '?' -> sb.append('.');
        case '.', '\\', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|' ->
            sb.append('\\').append(c);
        default -> sb.append(c);
      }
    }
    return sb.append('$').toString();
  }

  @Override
  public boolean match(String hostName) {
    Preconditions.checkState(!closed.get(), "Matcher is closed");
    if (StringUtils.isEmpty(hostName)) {
      return false;
    }
    if (InetAddresses.isInetAddress(hostName)) {
      return ipAddresses.contains(canonicalIp(hostName));
    }
    if (database == null) {
      return false; // No patterns, so non-IP host names don't match
    }
    Lock readLock = closeLock.readLock();
    try {
      readLock.lock();
      Preconditions.checkState(!closed.get(), "Matcher is closed");
      Scanner s = scanner.get();
      return s.hasMatch(database, hostName);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public void close(Promise<Void> completion) {
    if (closed.compareAndSet(false, true)) {
      if (database != null) {
        Lock writeLock = closeLock.writeLock();
        try {
          writeLock.lock();
          scanner.remove();
          closeQuietly(database);
        } finally {
          writeLock.unlock();
        }
      }
    }
    completion.complete();
  }
}
