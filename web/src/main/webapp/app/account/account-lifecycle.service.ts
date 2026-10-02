import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';

/**
 * The two calls behind the two addresses the activation and password-reset mails compose.
 *
 * <p><b>These are GATEWAY endpoints, so `getEndpointFor` takes no second argument</b> — that is what
 * addresses the gateway itself, and `MICROSERVICE` deliberately has no entry for it
 * (`app/config/microservices.ts` says so). A literal here would be refused by
 * `endpoint-construction.spec.ts`.
 *
 * <p><b>Both are `permitAll` and neither takes a token</b>, read off the gateway's own
 * `SecurityConfiguration`: `.pathMatchers("/api/activate").permitAll()` and
 * `.pathMatchers("/api/account/reset-password/finish").permitAll()`. They have to be — a person
 * following a link out of a mail message has no account they can sign in to yet, which is the whole
 * point of the activation one. The JWT interceptor attaches a token when one happens to be stored
 * and neither response changes.
 *
 * <p><b>⚠ The two paths are NOT the frontend paths with the origin swapped, and that is the thing to
 * carry.</b> The mail composes `/account/activate` and `/account/reset/finish`; the API is
 * `/api/activate` and `/api/account/reset-password/finish`. Only the first pair even rhymes, and the
 * second differs in segment count, in wording and in verb. A screen therefore has to know both
 * halves, and a reader who assumes a prefix rule will get the reset one wrong. Backlog NEW-60 says
 * so in as many words.
 *
 * <p><b>Both answer an EMPTY BODY on success, so both are typed `Observable<null>` rather than
 * `Observable<void>`.</b> That is what `HttpClient` actually produces for a 200 with no content
 * under the default `responseType: 'json'`, and `void` as a call-site type argument is refused by
 * `@typescript-eslint/no-invalid-void-type` here in any case. Nothing reads the value: both
 * subscribers branch on which arm fired.
 *
 * <p><b>It is named `AccountLifecycleService` rather than `AccountService` on purpose.</b>
 * `app/core/auth/account.service.ts` is generated and already exists (it reads `api/account` for the
 * signed-in identity), and `CLAUDE.md`'s standing rule is never to name a hand-written file after one
 * a generator produces. `databaseType: no` means JHipster generated no account screens here at all
 * (D101 §6), so nothing collides today — this is the cheap half of not relying on that.
 */
@Injectable({ providedIn: 'root' })
export class AccountLifecycleService {
  private readonly http = inject(HttpClient);
  private readonly applicationConfigService = inject(ApplicationConfigService);

  /**
   * `GET /api/activate?key=…` — measured against the quality gateway on 2026-10-02.
   *
   * <p>200 and an empty body on success; **400** for a key that matches no account, carrying
   * *"No user was found for this activation key"*. Not 500: `AccountResource`'s private
   * `AccountResourceException` carries `@ResponseStatus(BAD_REQUEST)`. Nothing here reads the body —
   * see `Activation`'s javadoc for why a visitor is told one thing for every failure.
   */
  activate(key: string): Observable<null> {
    const params = new HttpParams().set('key', key);
    return this.http.get<null>(this.applicationConfigService.getEndpointFor('api/activate'), { params });
  }

  /**
   * `POST /api/account/reset-password/finish` with `{ key, newPassword }`.
   *
   * <p>The body's field names are `KeyAndPasswordVM`'s and are read off it rather than guessed:
   * `key` and `newPassword`. 200 with an empty body on success; **400** both for a password outside
   * 4–100 characters (`InvalidPasswordException`, type `…/problem/invalid-password`) and for a key
   * that matches no account (`AccountResourceException`, title *"Account resource request
   * invalid"*). `NewPassword` tells those two apart by the problem `type`, because only one of them
   * is something the person at the keyboard can fix.
   */
  finishPasswordReset(key: string, newPassword: string): Observable<null> {
    return this.http.post<null>(this.applicationConfigService.getEndpointFor('api/account/reset-password/finish'), {
      key,
      newPassword,
    });
  }
}
