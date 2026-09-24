import { Routes } from '@angular/router';

/**
 * The three public screens. **No route here has a guard and none ever should** — every endpoint
 * behind them is `permitAll` in catalog, which is what makes this the part of the client anybody can
 * look at without an account.
 *
 * <p><b>`professionals/:ref` is public; `/pro/*` is reserved for Stage D's workspace.</b> The
 * backlog asks for that split explicitly, and the reason is a real defect in the prototype rather
 * than tidiness: its router tells `#/pro/p1` from `#/pro/profile` with a reserved-word list
 * duplicated at two call sites plus a `state.role` test, so a professional whose `reference` happened
 * to be `profile` was unreachable. Splitting the namespaces deletes the list and the defect together.
 *
 * <p>Discover is the application's landing page at `''`, with `discover` redirecting to it so the
 * prototype's own `#/discover` address still resolves for anybody following an old link.
 */
const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./discover/discover'),
    title: 'marketplace.discover.pageTitle',
  },
  {
    path: 'discover',
    redirectTo: '',
    pathMatch: 'full',
  },
  {
    path: 'browse',
    loadComponent: () => import('./browse/browse'),
    title: 'marketplace.browse.pageTitle',
  },
  {
    path: 'professionals/:ref',
    loadComponent: () => import('./professional/professional'),
    title: 'marketplace.professional.pageTitle',
  },
];

export default routes;
