import { Routes } from '@angular/router';

/**
 * **The two addresses the shipped mail templates compose, and no third one** — backlog NEW-60,
 * `decisions.md` D105.
 *
 * <p>The paths are not chosen here; they are read off
 * `gateway/src/main/resources/templates/mail/activationEmail.html` and `passwordResetEmail.html`,
 * which have composed `${baseUrl}/account/activate?key=…` and
 * `${baseUrl}/account/reset/finish?key=…` since the scaffold. Changing either string here breaks a
 * link that is already in somebody's inbox the day this estate sends mail, so <b>re-derive from the
 * templates rather than from this file</b>:
 *
 * <pre>
 *   grep -rhoE '\$\{baseUrl\}/[a-z/]+\?[a-z]+=' gateway/src/main/resources/templates/mail/*.html
 * </pre>
 *
 * <p><b>`reset/finish` is two segments and that is deliberate</b> — it mirrors the template exactly.
 * It is tempting to write `reset-finish` to match the component directory; that is a different URL
 * and the mail would not reach it.
 *
 * <p><b>No route here has a guard and none ever should.</b> Both API endpoints behind them are
 * `permitAll` in the gateway's `SecurityConfiguration`, and they have to be: somebody following an
 * activation link has no account they can sign in to yet. A `UserRouteAccessService` on either one
 * would send them to the login page, which is the 401 this item closes wearing a different costume.
 *
 * <p><b>There is deliberately NO "forgot password" request screen here.</b> The templates compose
 * `reset/finish` only; `POST /api/account/reset-password/init` is called from a screen that belongs
 * to NEW-48 Stage C. Two routes, matching two templates, and no third.
 *
 * <p>The file is `account-lifecycle.routes.ts` rather than `account.routes.ts` because the latter is
 * the name JHipster emits for this directory — `admin/admin.routes.ts` and `entities/entity.routes.ts`
 * in this very tree show the convention — and `CLAUDE.md`'s standing rule is never to name a
 * hand-written file after one a generator produces. `databaseType: no` means no account screens were
 * generated here at all (D101 §6), so nothing collides today; this is the cheap half of not relying
 * on that staying true.
 */
const routes: Routes = [
  {
    path: 'activate',
    loadComponent: () => import('./activation/activation'),
    title: 'activate.pageTitle',
  },
  {
    path: 'reset/finish',
    loadComponent: () => import('./new-password/new-password'),
    title: 'reset.finish.pageTitle',
  },
];

export default routes;
