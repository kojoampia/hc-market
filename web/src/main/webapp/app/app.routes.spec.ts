import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import routes from './app.routes';

/**
 * **Every public address resolves to a component, asked of the router rather than of the file.**
 *
 * <p>This file exists because of a defect that no other kind of test in this application could see,
 * found by loading the built client in a real browser against the live quality gateway and not by
 * anything in CI. `entity.routes` is the generated `Routes = [/* needle *\/]` — an EMPTY array
 * behind `path: ''`. Ordered before the marketplace, it matched the empty URL, consumed it, found
 * no child to render, and <b>did not backtrack</b>: the landing page was a navbar over an empty
 * outlet.
 *
 * <p><b>Nothing was red.</b> Lint, prettier, 260 unit tests, the production build and
 * `check:built-assets` were all green, because a component spec instantiates its component directly
 * and never asks the router anything. `/browse` rendered eighteen cards the whole time, which is
 * what made the failure look like a styling problem rather than a routing one.
 *
 * <p>So the assertion is `router.navigateByUrl(...)` followed by <b>"something was activated"</b>,
 * which is the only question a route table can be wrong about in this way.
 *
 * <p><b>There is deliberately no outlet here and the components are never constructed.</b> An
 * earlier draft rendered them through a host with a `<router-outlet>`, which is a stronger test and
 * a worse one: it makes this file's result depend on every screen it touches being constructible in
 * a bare `TestBed`, and the generated `Login` immediately produced an unhandled NG0951 from its own
 * `ngAfterViewInit`. The subject here is the ROUTE TABLE; each screen's own spec constructs it.
 */
describe('app.routes', () => {
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    router = TestBed.inject(Router);
  });

  /** The component the primary outlet would render, or `null` if the outlet is empty. */
  const activatedAt = async (url: string): Promise<unknown> => {
    await router.navigateByUrl(url);
    let route = router.routerState.root;
    while (route.firstChild) route = route.firstChild;
    return route.routeConfig?.loadComponent ?? route.component ?? null;
  };

  /**
   * **`not.toBeNull()` IS NOT "THIS ADDRESS IS SERVED", AND THAT WAS MEASURED HERE — D105 §5.**
   *
   * <p>`...errorRoute` is a wildcard at the foot of the table, so an address this application does
   * **not** serve activates the error page rather than nothing. `activatedAt` then returns a
   * perfectly non-null `loadComponent` and *"something was activated"* is satisfied by the 404.
   *
   * <p><b>Measured, on this file, before the account routes existed</b>: the two
   * `it.each(['/account/activate?key=abc', '/account/reset/finish?key=abc'])` cases below were
   * **GREEN** against a table with no `account` entry at all, because both URLs fell through here.
   * The only two cases that went red were the ones comparing the two screens *against each other*
   * and the one reading the table's own ordering. So the assertion a reader would call the subject of
   * this file could not see its own subject — this repository's signature defect, re-earned inside
   * the commit that was closing an instance of it.
   *
   * <p>So every "this address is served" assertion compares against the error page's own component.
   * `/nothing-at-this-address` is what names it, derived rather than imported, because the question
   * is <i>what does an unserved URL activate</i> and only the router can answer that.
   */
  const errorPage = async (): Promise<unknown> => activatedAt('/nothing-at-this-address');

  const expectServed = async (url: string): Promise<unknown> => {
    const activated = await activatedAt(url);
    expect(activated, `${url} activates nothing — the outlet would be empty`).not.toBeNull();
    expect(activated, `${url} is NOT SERVED: it falls through to the error page`).not.toBe(await errorPage());
    return activated;
  };

  it.each(['/', '/discover', '/browse', '/professionals/p1'])('activates a component at %s', async url => {
    await expectServed(url);
  });

  /**
   * **The two addresses the SHIPPED MAIL TEMPLATES compose — NEW-60, D105.**
   *
   * <p>`gateway/src/main/resources/templates/mail/activationEmail.html` composes
   * `${baseUrl}/account/activate?key=…` and `passwordResetEmail.html` composes
   * `${baseUrl}/account/reset/finish?key=…`. These are the literal strings, re-derived off the two
   * templates rather than copied from an item, and the second one is **not the API path with the
   * origin swapped**: the API is `POST /api/account/reset-password/finish`, so the two differ by
   * more than a prefix.
   *
   * <p><b>Until this commit the client had no `account/` route at all</b>, and on an API-only estate
   * following the link therefore answered **401** — reactive Spring Security denies an exchange no
   * `authorizeExchange` rule matched, so a person was asked to authenticate in order to reach the
   * page that exists to let them authenticate. Measured against the quality gateway on 2026-10-02:
   * `GET http://127.0.0.1:15509/account/activate?key=zzz-not-a-key` → **401**, while
   * `GET /api/activate?key=zzz-not-a-key` → **400** with *"No user was found for this activation
   * key"*.
   *
   * <p>⚠ **A ROUTE EXISTING IS ONE OF THREE CONDITIONS AND IT IS THE ONLY ONE THIS COMMIT CLOSES.**
   * The client is served at no origin, so `JHIPSTER_MAIL_BASE_URL` still honestly names the API's
   * edge and the link still answers 401 on every estate. D105 §3.
   */
  it.each(['/account/activate?key=abc', '/account/reset/finish?key=abc'])(
    'SERVES %s — the address the mail actually composes',
    async url => {
      await expectServed(url);
    },
  );

  it('serves the two account screens WITHOUT a key, rather than falling through to the error page', async () => {
    // A link truncated by a mail client arrives with no query string at all, and that must still be
    // the account screen saying so — not the 404 page, which is what a route table keyed on the
    // query would give. The key is read inside the component, where a missing one is a DECIDED state
    // and never a request carrying `key=undefined`.
    expect(await expectServed('/account/activate')).toBe(await activatedAt('/account/activate?key=abc'));
    expect(await expectServed('/account/reset/finish')).toBe(await activatedAt('/account/reset/finish?key=abc'));
  });

  it('activates a DIFFERENT component for each of the two account screens', async () => {
    // The control. "This address is served" is satisfied by a table that sends both mail links to
    // ONE screen — which would activate a password form from an activation link, or the reverse.
    // This is one of only two cases in this file that went red before the routes existed; the other
    // is the ordering one below. See `expectServed`'s javadoc for why that matters.
    const activate = await expectServed('/account/activate?key=abc');
    const reset = await expectServed('/account/reset/finish?key=abc');
    expect(activate).not.toBe(reset);
    expect(activate).not.toBe(await activatedAt('/'));
    expect(reset).not.toBe(await activatedAt('/'));
  });

  /**
   * **THE ORDERING CASE, AND IT IS NOT THE ONE THIS PACKAGE SET OUT TO WRITE — D105 §5.**
   *
   * <p>D103's defect is an EMPTY children array behind `path: ''` swallowing the empty URL, and the
   * obvious generalisation is that such a parent swallows everything below it. **It does not, and
   * that was measured here rather than reasoned**: with the `account` entry moved below *both*
   * `path: ''` parents, all four behavioural assertions above stayed **green** and the only thing
   * that fired was a positional assertion comparing two array indices.
   *
   * <p><b>So that positional assertion was DELETED rather than kept.</b> A `path: ''` parent consumes
   * nothing of a named URL, finds no child, and the router moves on to the next sibling — ordinary
   * backtracking. The terminal case is the empty URL alone, where the parent has consumed the whole
   * of it and there is nothing left to try. A test pinning the position would therefore have pinned a
   * coincidence and gone red on a correct change, which is the mistake `CLAUDE.md` names about
   * `BADGE_ZONE` and `MARKET_ZONE`.
   *
   * <p><b>What IS load-bearing is `...errorRoute` staying last</b>, and this case is that. Measured
   * with the wildcard moved above the `account` entry: **ten of twelve** cases red, including `/`,
   * `/browse` and both mail addresses. That is loud rather than silent, which is why it gets one
   * cheap assertion and not a paragraph.
   */
  it('keeps the wildcard error route last, so nothing above it is unreachable', async () => {
    const wildcardAt = routes.findIndex(route => route.path === '**');
    expect(wildcardAt, 'no `**` route — this case is checking nothing').toBeGreaterThanOrEqual(0);
    expect(wildcardAt, 'the wildcard is not last: every route below it is unreachable').toBe(routes.length - 1);
    // …and the behaviour the position is for, asked of the router, because a position asserted
    // without what it is positioned against is a number rather than a property (D67 §11).
    await expectServed('/account/activate?key=abc');
    await expectServed('/account/reset/finish?key=abc');
  });

  it('activates a DIFFERENT component for the profile than for Discover', async () => {
    // The control. "Something was activated" is satisfied by a table that sends every URL to one
    // screen, which is the other way a reorder goes wrong.
    expect(await activatedAt('/professionals/p1')).not.toBe(await activatedAt('/'));
    expect(await activatedAt('/browse')).not.toBe(await activatedAt('/'));
  });

  it('sends /discover to the same component as /', async () => {
    // The prototype addresses `#/discover`; the redirect is what keeps an old link working.
    expect(await activatedAt('/discover')).toBe(await activatedAt('/'));
  });

  it('still reaches login and the error page', async () => {
    // A `path: ''` parent placed ahead of them must not shadow a named route.
    expect(await activatedAt('/login')).not.toBeNull();
    expect(await activatedAt('/nothing-at-this-address')).not.toBeNull();
  });
});
