import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access.service';
import { Authority } from 'app/shared/jhipster/constants';

import { errorRoute } from './layouts/error/error.route';

const routes: Routes = [
  /**
   * THE MARKETPLACE IS THE LANDING PAGE, AND THE GENERATED HOME IS DELETED RATHER THAN MOVED.
   *
   * <p>`./home/home` was JHipster's "Welcome, Java Hipster!" page, and on a PUBLIC marketplace
   * client it was not merely off-brand: its `global.messages.info.authenticated.suffix` reads
   * <i>"you can try the default accounts: Administrator (login="admin" and password="admin")"</i>,
   * and on the estates that run `dev`/`test` that credential is real (D61). The three keys that
   * composed it went with it, so the string is not in the bundle either — an unrendered i18n entry
   * still ships and is still fetchable.
   *
   * <p>Stage B's own screens are in `./marketplace/marketplace.routes`. **They are declared AFTER
   * `login` and `admin` and BEFORE `entity.routes`, and both halves of that matter** — see the ⚠
   * block below, which is the half a reader must not skip.
   *
   * > This sentence read *"loaded last so `''`, `browse` and `professionals/:ref` cannot shadow
   * > `login` or `admin`"* until 2026-09-24, and **both clauses were wrong**. The marketplace is not
   * > last — `entity.routes` and `...errorRoute` follow it, and it **must** precede the first of
   * > those or the landing page is empty. And it is not what keeps `login` reachable: `login` and
   * > `admin` are declared *above* the marketplace, so nothing below them could shadow them anyway.
   * > A reader who trusted the first clause and moved this entry to the end would rebuild the defect
   * > the ⚠ block exists to describe, with every gate green.
   */
  {
    path: '',
    loadComponent: () => import('./layouts/navbar/navbar'),
    outlet: 'navbar',
  },
  {
    path: 'admin',
    data: {
      authorities: [Authority.ADMIN],
    },
    canActivate: [UserRouteAccessService],
    loadChildren: () => import('./admin/admin.routes'),
  },
  {
    path: 'login',
    loadComponent: () => import('./login/login'),
    title: 'login.title',
  },
  /**
   * ⚠ THE TWO ADDRESSES THE ACTIVATION AND PASSWORD-RESET MAILS COMPOSE — NEW-60, D105.
   *
   * <p>`${baseUrl}/account/activate?key=…` and `${baseUrl}/account/reset/finish?key=…`, read off
   * the two shipped templates in `gateway/src/main/resources/templates/mail/`. Nothing in this
   * client served either until 2026-10-02, so following the link on an API-only estate answered
   * <b>401</b> — a credential challenge for the page that exists to let somebody authenticate.
   *
   * <p>It is declared above both `path: ''` entries so this array reads in the order a URL is
   * tried. ⚠ <b>That placement is NOT load-bearing, and the first version of this comment said it
   * was — measured, D105 §5.</b> The ⚠ block below is about the EXACT EMPTY URL: a `path: ''` parent
   * with an empty children array consumes `''` and has nothing to render, which is the terminal case
   * that cannot backtrack. A NAMED sibling is a different question, and the answer is the opposite —
   * with this entry moved below both empty-path parents, all four behavioural assertions in
   * `app.routes.spec.ts` stayed <b>green</b> and only a positional assertion fired. That positional
   * assertion was then deleted rather than kept, because a test pinning a position with no
   * behavioural consequence pins a coincidence and goes red on a correct change.
   *
   * <p>So <b>do not read the ⚠ block below as applying to this entry.</b> What keeps these two URLs
   * reachable is `...errorRoute` staying last — moved above this entry, the wildcard swallows
   * everything and ten of twelve cases go red at once, which is loud rather than silent.
   *
   * <p>⚠ <b>Serving the route is ONE of three conditions for the link working, and the only one this
   * commit closes.</b> The client is built and served at no origin (Phase 5), so
   * `JHIPSTER_MAIL_BASE_URL` still honestly names the API edge and the link still answers 401
   * everywhere. The five documents that say so remain TRUE — D105 §3 lists them and says not to
   * "update" any of them.
   */
  {
    path: 'account',
    loadChildren: () => import('./account/account-lifecycle.routes'),
  },
  /**
   * ⚠ THE MARKETPLACE MUST COME BEFORE `entity.routes`, AND THE REASON IS A MEASUREMENT.
   *
   * <p>`entity.routes` is the generated `Routes = [/* jhipster-needle-add-entity-route *\/]` — an
   * EMPTY array behind `path: ''`. Placed first, it matches the empty URL, consumes it, finds no
   * child to render, and <b>does not backtrack</b>, so the primary outlet stays empty and the
   * landing page is a navbar over nothing.
   *
   * <p><b>It is silent, and it is silent in the worst possible way</b>: the shell renders, the
   * navbar renders, no console error appears, `npm run lint`, `npm test` and `npm run webapp:prod`
   * are all green, and every unit spec passes because a spec instantiates the component directly
   * rather than routing to it. Measured against the running quality gateway on 2026-09-24:
   * `/browse` rendered 18 cards while `/` rendered an empty `<router-outlet>` — which is what
   * establishes that the empty children array swallows exactly `''` rather than everything, and is
   * why reordering is the fix rather than deleting the entities entry.
   *
   * <p>Putting the marketplace <b>ahead of the entities entry</b> — not first in this array, which is
   * what the note above disclaims — costs nothing: its children are `''`, `discover`, `browse` and
   * `professionals/:ref`, and a URL matching none of them backtracks normally, because backtracking
   * happens when a parent matches and a child does not — which is precisely what an empty children
   * array cannot do.
   */
  {
    path: '',
    loadChildren: () => import('./marketplace/marketplace.routes'),
  },
  {
    path: '',
    loadChildren: () => import('./entities/entity.routes'),
  },
  ...errorRoute,
];

export default routes;
