package org.deadbeef.route;

/**
 * The per-host routing decision. {@link DefaultRoutePolicy} is the production implementation; the
 * interface exists so handlers depend on the decision rather than its computation, and so it can be
 * stubbed in tests.
 */
public interface RoutePolicy {

  Decision decide(String host);

  /** What a handler should do with a request to a given host. */
  enum Decision {
    /** Always route through the remote proxy (known-blocked); skip the direct attempt. */
    REMOTE,
    /** Always go direct, hard-pinned (a connect failure errors rather than falling back). */
    DIRECT,
    /** Unlisted: try direct first, fall back to the remote proxy on connect failure. */
    GATE
  }
}
