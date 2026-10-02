import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { TranslateDirective } from 'app/shared/language';

import LoadState from 'app/marketplace/load-state/load-state';
import { drive, loadableSignal } from 'app/marketplace/load-state';
import { paramValue } from 'app/marketplace/route-params';

import { AccountLifecycleService } from '../account-lifecycle.service';

/**
 * `/account/activate?key=…` — **the address the activation mail has always composed**, and until
 * this screen existed nothing in this estate served it (backlog NEW-60, `decisions.md` D105).
 *
 * <p>`gateway/src/main/resources/templates/mail/activationEmail.html` builds the link as
 * `${baseUrl}/account/activate?key=…`, which is JHipster's convention and is what
 * `JHIPSTER_MAIL_BASE_URL` names: a **frontend** route. On an API-only estate the only origin an
 * operator could honestly put there was the gateway's own edge, where that path matches no route and
 * reactive Spring Security **denies an exchange no `authorizeExchange` rule matched** — so following
 * the link answered **401**, and a person was asked to authenticate in order to reach the page that
 * exists to let them authenticate. Re-measured on the quality gateway, 2026-10-02:
 * `GET /account/activate?key=zzz-not-a-key` → **401**.
 *
 * <p>⚠ <b>BUILDING THIS SCREEN DOES NOT MAKE THE LINK WORK ON ANY ESTATE, AND THE NEXT READER OF
 * THIS FILE IS WHO NEEDS TO KNOW THAT.</b> A working link needs three things and this is one:
 *
 * <ol>
 *   <li>the route exists in this client — <b>closed here</b>;</li>
 *   <li>the client is <b>built and served at some origin</b> — undecided, Phase 5. `web/` has never
 *       been deployed anywhere; it runs under `npm start` against a gateway and nothing else;</li>
 *   <li>`JHIPSTER_MAIL_BASE_URL` <b>names that origin</b> — an operator's, at deploy time.</li>
 * </ol>
 *
 * So `secrets.env.example`, `deploy-prod.sh`'s hint, `quality/compose.yml`,
 * `application-prod.yml` and `deploy/prod-server/README.md` all still say the link answers 401 and
 * that the key in it activates through `GET /api/activate` — and <b>they are all still true</b>.
 * Do not "update" them on the strength of this file existing; D105 §3.
 *
 * <p><b>Three states, through `app/marketplace/load-state/`, plus a fourth that makes no request.</b>
 * A link truncated by a mail client arrives with no `key` at all, and that is a DECIDED state — not
 * a `GET` carrying `key=undefined`, which the gateway answers 400 for
 * (*"Required query parameter 'key' is not present"*, measured) and which would tell a visitor the
 * activation failed when nothing was ever attempted.
 *
 * <p><b>Every failure renders identically and none of them shows a status code.</b> The estate
 * distinguishes a key that matches nobody (**400**, *"No user was found for this activation key"*)
 * from an estate that could not be reached (anything else), and a visitor can act on neither: both
 * end in "use the registration form again". That is also the generated bundle's own wording —
 * `activate.messages.error` has said *"Please use the registration form to sign up"* since the
 * scaffold — so the copy is not invented here. It is the same call `professional.ts` makes for a 404
 * against a 502, and for the same reason: a page that distinguishes its refusals is an oracle, and
 * an already-activated account is one of the two.
 */
@Component({
  selector: 'abm-activation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './activation.html',
  styleUrl: './activation.scss',
  imports: [RouterLink, TranslateDirective, LoadState],
})
export default class Activation implements OnInit {
  /** `null` throughout — the endpoint answers an empty body. Only `status` is ever read. */
  readonly outcome = loadableSignal<null>();

  /**
   * `undefined` when the query string carries no usable key.
   *
   * <p>Read through `paramValue` and not `paramMap.get('key')`: `endpoint-construction.spec.ts`
   * keys on the METHOD NAME, so a literal `.get('key')` is an offender to it whatever the receiver
   * is. `route-params.ts` exists for exactly this and its javadoc names this screen family. It also
   * collapses `?key=` to `undefined`, which is the right reading — an empty key is not a key, and
   * the server would answer 400 for it.
   */
  readonly key = signal<string | undefined>(undefined);

  readonly screen = computed<'loading' | 'nokey' | 'activated' | 'failed'>(() => {
    if (this.key() === undefined) return 'nokey';
    switch (this.outcome().status) {
      case 'ready':
        return 'activated';
      case 'failed':
        return 'failed';
      default:
        return 'loading';
    }
  });

  private readonly accounts = inject(AccountLifecycleService);
  private readonly route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      this.key.set(paramValue(params, 'key'));
      this.activate();
    });
  }

  /** The retry arm. Re-asking with the same key is the right action: the usual failure is a restart. */
  activate(): void {
    const key = this.key();
    if (key === undefined) return;
    drive(this.outcome, this.accounts.activate(key));
  }
}
