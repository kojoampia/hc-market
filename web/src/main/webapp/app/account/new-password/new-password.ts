import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { TranslateDirective } from 'app/shared/language';
import { INVALID_PASSWORD_TYPE } from 'app/shared/jhipster/error.constants';

import LoadState from 'app/marketplace/load-state/load-state';
import { paramValue } from 'app/marketplace/route-params';

import { AccountLifecycleService } from '../account-lifecycle.service';

/**
 * The bounds this FORM enforces, and only the first of the two is the gateway's.
 *
 * <p>⚠ **These are NOT both `ManagedUserVM`'s, and this comment said they were.** Measured on the
 * gateway: `ManagedUserVM.PASSWORD_MIN_LENGTH` is **4** and `PASSWORD_MAX_LENGTH` is **100**. The 50
 * below is the **generated translation bundle's** number —
 * `global.messages.validate.newpassword.maxlength` reads *"Your password cannot be longer than 50
 * characters"* — and it is deliberately **narrower than the server's**.
 *
 * <p>The value is right and the provenance claim was wrong, which matters because this is exactly
 * the comment somebody reads when deciding whether 50 may be widened to 100. It may — the server
 * accepts it — but **the bundle's sentence has to move with it**, in every language, or the form
 * accepts what its own message forbids. That is the worse of the two mismatches and is why the
 * narrow number was chosen; see this class's javadoc.
 *
 * <p>Nothing here trusts the client's rule away: `isPasswordLengthInvalid` runs on the gateway
 * regardless, and its refusal is surfaced rather than suppressed (`refusalFrom`'s `password` arm).
 * A client validator is a courtesy.
 */
const PASSWORD_MIN_LENGTH = 4;
const PASSWORD_MAX_LENGTH = 50;

/** Which of the three things went wrong, because only one of them is the person's to fix. */
type Refusal = 'password' | 'key' | 'unreachable';

/**
 * `/account/reset/finish?key=…` — **the second address the shipped mail templates compose**, and the
 * one a prefix rule gets wrong (backlog NEW-60, `decisions.md` D105).
 *
 * <p>`gateway/src/main/resources/templates/mail/passwordResetEmail.html` builds the link as
 * `${baseUrl}/account/reset/finish?key=…`. ⚠ <b>That is NOT the API's path with the origin
 * swapped</b>: the API is `POST /api/account/reset-password/finish`. The two differ in segment
 * count, in spelling and in verb, so this screen has to know both halves and nothing about the
 * frontend address tells you the back-end one. NEW-60 states this explicitly and it is the reason
 * `AccountLifecycleService` holds both literals in one place.
 *
 * <p>⚠ <b>SERVING THIS ROUTE DOES NOT MAKE THE MAIL LINK WORK.</b> Three conditions, one closed
 * here — see `Activation`'s javadoc, which carries the list, and D105 §3. The five documents saying
 * the link answers 401 remain true.
 *
 * <p><b>Four states, and the first one makes no request and renders no form.</b> With no `key` in
 * the query string there is nothing to submit against, so the form is not shown at all — a form that
 * collects a password and then fails for a reason the person cannot see is worse than a page saying
 * the link is incomplete. `reset.finish.messages.keymissing` has been in the generated bundle since
 * the scaffold and is exactly this sentence.
 *
 * <p><b>THREE refusals, and they do not render identically — which is the opposite of
 * `Activation`'s call, on purpose.</b> Both arrive as **400** from the same endpoint, and the
 * discriminator is the problem `type`:
 *
 * <table>
 *   <tr><th>what happened</th><th>how it is told apart</th><th>what the person is told</th></tr>
 *   <tr><td>the password is outside 4–100</td><td>`type` is `…/problem/invalid-password`</td>
 *       <td>the password rule, <b>with the form still on screen</b></td></tr>
 *   <tr><td>the key matches no account</td><td>any other 400</td>
 *       <td>the link has expired — ask for a new one</td></tr>
 *   <tr><td>the estate could not be reached</td><td>status 0, or any 5xx</td>
 *       <td>try again in a moment, <b>with the form still on screen</b></td></tr>
 * </table>
 *
 * <p><b>The asymmetry with `Activation` is a decision rather than an inconsistency.</b> There, every
 * failure ends in the same action ("register again") and one of the two readings is "this account is
 * already active", which makes a distinguishing page an existence oracle. Here the person has
 * **typed** something, one of the three readings is *"what you typed is too short"*, and withholding
 * that leaves them retyping the same password against a page that will refuse it again. The key arm
 * discloses nothing a holder of the link does not already have: they were sent it.
 *
 * <p><b>The server's length rule is enforced on the client too, and the client's is NARROWER.</b>
 * The gateway accepts 4–100; `global.messages.validate.newpassword.maxlength` — the generated
 * bundle, unchanged — tells a person 50. So 50 is the number this form enforces, because a form that
 * accepts what its own message forbids is the worse of the two mismatches. The server's refusal is
 * still surfaced rather than trusted away: `isPasswordLengthInvalid` runs regardless and a client
 * validator is a courtesy, not a guarantee.
 */
@Component({
  selector: 'abm-new-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './new-password.html',
  styleUrl: './new-password.scss',
  imports: [RouterLink, ReactiveFormsModule, TranslateDirective, LoadState],
})
export default class NewPassword implements OnInit {
  readonly minLength = PASSWORD_MIN_LENGTH;
  readonly maxLength = PASSWORD_MAX_LENGTH;

  readonly form = new FormGroup({
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)],
    }),
    confirmPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)],
    }),
  });

  readonly submitting = signal(false);
  readonly done = signal(false);
  readonly refusal = signal<Refusal | null>(null);
  /** Client-side, and therefore never sent: the two boxes disagree. */
  readonly mismatch = signal(false);

  /** `undefined` when the link carries no usable key — read through `paramValue`, see `ngOnInit`. */
  readonly key = signal<string | undefined>(undefined);

  readonly screen = computed<'nokey' | 'form' | 'submitting' | 'done'>(() => {
    if (this.key() === undefined) return 'nokey';
    if (this.done()) return 'done';
    return this.submitting() ? 'submitting' : 'form';
  });

  /** The two refusals that leave the form up, so the person can act on what they are told. */
  readonly refusalOnForm = computed<Refusal | null>(() => {
    const refusal = this.refusal();
    return refusal === 'key' ? null : refusal;
  });

  readonly expired = computed(() => this.refusal() === 'key');

  private readonly accounts = inject(AccountLifecycleService);
  private readonly route = inject(ActivatedRoute);

  /**
   * Which of the three refusals this response is.
   *
   * <p><b>The default is `unreachable` and not `key`</b>, which is the fail-safe direction: telling
   * somebody with a perfectly good link that it has expired sends them back to request another one
   * and leaves them there, while telling somebody with an expired link to try again costs them one
   * more attempt and the real message on it. Only a response that actually says 400 is read as a
   * statement about the key.
   */
  private static refusalFrom(response: unknown): Refusal {
    if (!(response instanceof HttpErrorResponse)) return 'unreachable';
    if (response.status !== 400) return 'unreachable';
    const type: unknown = (response.error as { type?: unknown } | null)?.type;
    return type === INVALID_PASSWORD_TYPE ? 'password' : 'key';
  }

  ngOnInit(): void {
    // `paramValue`, not `paramMap.get('key')` — `endpoint-construction.spec.ts` keys on the method
    // name and a literal `.get('key')` is an offender to it whatever the receiver is.
    this.route.queryParamMap.subscribe(params => this.key.set(paramValue(params, 'key')));
  }

  submit(): void {
    const key = this.key();
    if (key === undefined) return;

    const { newPassword, confirmPassword } = this.form.getRawValue();
    this.mismatch.set(newPassword !== confirmPassword);
    if (this.mismatch() || this.form.invalid) {
      // Nothing is sent. A mismatch is not a fact the server can establish — it is only ever handed
      // one password — so asking it would mean sending one of the two and calling the answer a
      // match.
      this.refusal.set(null);
      return;
    }

    this.submitting.set(true);
    this.refusal.set(null);
    this.accounts.finishPasswordReset(key, newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.done.set(true);
      },
      // An `error` arm is not optional: `subscribe({ next })` alone RETHROWS, and RxJS reports it as
      // an unhandled error — in a browser, an uncaught exception on a page whose own failure state
      // renders perfectly well. It cost three of those in Stage B with every assertion green.
      error: (response: unknown) => {
        this.submitting.set(false);
        this.refusal.set(NewPassword.refusalFrom(response));
      },
    });
  }
}
