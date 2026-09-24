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

  it.each(['/', '/discover', '/browse', '/professionals/p1'])('activates a component at %s', async url => {
    expect(await activatedAt(url), `${url} activates nothing — the outlet would be empty`).not.toBeNull();
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
