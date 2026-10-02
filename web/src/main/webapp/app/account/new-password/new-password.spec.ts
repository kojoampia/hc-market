import { beforeEach, describe, expect, it, vitest } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { provideTranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { INVALID_PASSWORD_TYPE } from 'app/shared/jhipster/error.constants';
import { loadEnglish } from 'app/marketplace/marketplace.fixtures';

import { AccountLifecycleService } from '../account-lifecycle.service';

import NewPassword from './new-password';

/**
 * **`/account/reset/finish?key=…` — the second address the shipped templates compose.**
 *
 * <p>The three refusals asserted here are all **400 from one endpoint** and are told apart by the
 * problem `type`, read off the gateway rather than guessed: `InvalidPasswordException` carries
 * `…/problem/invalid-password` and `AccountResourceException` carries
 * `@ResponseStatus(BAD_REQUEST)` with the title *"Account resource request invalid"*. The password
 * bounds are `ManagedUserVM.PASSWORD_MIN_LENGTH`/`MAX_LENGTH`, which is 4 and 100 on the server; the
 * client enforces 4–50 because that is what the generated bundle's own message tells a person.
 */
describe('NewPassword', () => {
  let fixture: ComponentFixture<NewPassword>;
  let accounts: { finishPasswordReset: ReturnType<typeof vitest.fn> };
  let queryParams: Record<string, string>;

  const create = async (): Promise<HTMLElement> => {
    await TestBed.configureTestingModule({
      imports: [NewPassword],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: AccountLifecycleService, useValue: accounts },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of({
              get: (key: string) => queryParams[key] ?? null,
              has: (key: string) => key in queryParams,
              getAll: (key: string) => (key in queryParams ? [queryParams[key]] : []),
              keys: Object.keys(queryParams),
            }),
          },
        },
      ],
    }).compileComponents();
    // THE REAL BUNDLE — see `activation.spec.ts`, which says why in full. Without it every
    // assertion over rendered text below would be reading a translation KEY.
    loadEnglish();
    fixture = TestBed.createComponent(NewPassword);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  const typeIn = (element: HTMLElement, password: string, confirmation = password): void => {
    for (const [cy, value] of [
      ['resetPassword', password],
      ['resetConfirm', confirmation],
    ] as const) {
      const input = element.querySelector(`[data-cy="${cy}"]`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
  };

  const submit = (element: HTMLElement): void => {
    (element.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  const refusedWith = (type: string): Observable<never> =>
    throwError(() => new HttpErrorResponse({ status: 400, error: { type, status: 400 } }));

  beforeEach(() => {
    queryParams = { key: 'a-real-looking-key' };
    accounts = { finishPasswordReset: vitest.fn(() => of(null)) };
  });

  it('sends the key from the query string with the new password, and says it is done', async () => {
    const element = await create();
    typeIn(element, 'a-good-password');
    submit(element);
    expect(accounts.finishPasswordReset).toHaveBeenCalledWith('a-real-looking-key', 'a-good-password');
    expect(element.querySelector('[data-cy="resetSuccess"]')).not.toBeNull();
    expect(element.textContent).toContain('password has been reset');
    expect(element.querySelector('[data-cy="resetSignIn"]')?.getAttribute('href')).toBe('/login');
  });

  it("surfaces the SERVER's refusal of a password, with the form still on screen", async () => {
    // ⚠ THE CLIENT VALIDATES 4–50 TOO, so this arm is only reachable for a password the client
    // accepts and the server refuses — and it is driven by the RESPONSE rather than by the input,
    // which is the honest way to exercise "the server said no". The password typed below is
    // perfectly valid client-side; the stub refuses it with the gateway's own problem type. A test
    // that reached this state by typing something the form already rejects would be asserting over
    // the client's own validator and calling it the server's.
    accounts.finishPasswordReset = vitest.fn(() => refusedWith(INVALID_PASSWORD_TYPE));
    const element = await create();
    typeIn(element, 'a-password-the-client-is-happy-with');
    submit(element);
    expect(element.querySelector('[data-cy="resetRefusedPassword"]')).not.toBeNull();
    // The form is STILL THERE, because retyping is the action and taking the boxes away forbids it.
    expect(element.querySelector('form')).not.toBeNull();
    expect(element.querySelector('[data-cy="resetSuccess"]')).toBeNull();
    expect(element.textContent).toContain('That password was refused');
    // The two bounds come from the component's constants through `translateValues`, not from prose,
    // because the client's 50 and the gateway's 100 are different numbers.
    expect(element.textContent).toContain('between 4 and 50');
  });

  it('tells a person with an EXPIRED key something different, and takes the form away', async () => {
    // Same status, same endpoint, different problem type — and the only refusal retyping cannot
    // fix, so leaving the boxes up would invite exactly that.
    accounts.finishPasswordReset = vitest.fn(() => refusedWith('https://www.jhipster.tech/problem/problem-with-message'));
    const element = await create();
    typeIn(element, 'a-good-password');
    submit(element);
    expect(element.querySelector('[data-cy="resetExpired"]')).not.toBeNull();
    expect(element.querySelector('form')).toBeNull();
    expect(element.textContent).toContain('no longer valid');
    // …and it names NO action, because this client has no password-reset request screen and no
    // registration screen to send anybody to — NEW-90. Copy that points at one is a promise the
    // client cannot keep, which is the defect class this repository is about.
    expect(element.textContent).not.toContain('sign-in page');
  });

  it('tells a person with an UNREACHABLE estate to try again, and keeps the form', async () => {
    // Not a statement about the key. Telling somebody with a perfectly good link that it expired
    // sends them to request another one and leaves them there; this is the fail-safe direction.
    accounts.finishPasswordReset = vitest.fn(() => throwError(() => new HttpErrorResponse({ status: 503 })));
    const element = await create();
    typeIn(element, 'a-good-password');
    submit(element);
    expect(element.querySelector('[data-cy="resetUnreachable"]')).not.toBeNull();
    expect(element.querySelector('[data-cy="resetExpired"]')).toBeNull();
    expect(element.querySelector('form')).not.toBeNull();
  });

  it('reads a transport failure that is not an HttpErrorResponse as unreachable, not as an expired key', async () => {
    // The default arm. `refusalFrom` returns `unreachable` for anything it cannot read as a 400,
    // and this is what makes that a decision rather than a fall-through nobody drove.
    accounts.finishPasswordReset = vitest.fn(() => throwError(() => new Error('socket closed')));
    const element = await create();
    typeIn(element, 'a-good-password');
    submit(element);
    expect(element.querySelector('[data-cy="resetUnreachable"]')).not.toBeNull();
    expect(element.querySelector('[data-cy="resetExpired"]')).toBeNull();
  });

  it('lets the subscription NOT rethrow when the estate is down', async () => {
    // A `subscribe({ next })` with no `error` arm RETHROWS, and in a browser that is an uncaught
    // exception on a page whose own failure state renders correctly. Asserted through the rendered
    // state rather than through "nothing threw", which is what survives review.
    accounts.finishPasswordReset = vitest.fn(() => throwError(() => new HttpErrorResponse({ status: 500 })));
    const element = await create();
    typeIn(element, 'a-good-password');
    submit(element);
    expect(element.querySelector('[data-cy="resetUnreachable"]')).not.toBeNull();
    expect(element.querySelector('[data-cy="stateLoading"]')).toBeNull();
  });

  it('sends nothing when the two boxes disagree', async () => {
    // A mismatch is not a fact the server can establish — it is only ever handed one password — so
    // asking it would mean sending one of the two and calling the answer a match.
    const element = await create();
    typeIn(element, 'a-good-password', 'a-different-password');
    submit(element);
    expect(accounts.finishPasswordReset).not.toHaveBeenCalled();
    expect(element.querySelector('[data-cy="resetMismatch"]')).not.toBeNull();
    expect(element.textContent).toContain('do not match');
  });

  it('sends nothing when the password is shorter than the server would accept', async () => {
    const element = await create();
    typeIn(element, 'abc');
    submit(element);
    expect(accounts.finishPasswordReset).not.toHaveBeenCalled();
    expect(element.querySelector('[data-cy="resetSuccess"]')).toBeNull();
    expect(element.querySelector('[data-cy="resetPasswordInvalid"]')).not.toBeNull();
    expect(element.textContent).toContain('at least 4 characters');
  });

  it('renders NO FORM and asks nothing when the link carries no key', async () => {
    // ⚠ There is nothing to submit a password against. A form that collects one and then fails for
    // a reason nobody can see is worse than saying the link is incomplete here.
    // `reset.finish.messages.keymissing` is the generated bundle's own sentence.
    queryParams = {};
    const element = await create();
    expect(element.querySelector('form')).toBeNull();
    expect(accounts.finishPasswordReset).not.toHaveBeenCalled();
    expect(element.querySelector('[data-cy="stateEmpty"]')).not.toBeNull();
    expect(element.textContent).toContain('reset key is missing');
  });

  it('treats a blank key as no key at all', async () => {
    queryParams = { key: '  ' };
    const element = await create();
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('[data-cy="stateEmpty"]')).not.toBeNull();
  });

  it('renders the submitting state while the request is in flight', async () => {
    accounts.finishPasswordReset = vitest.fn(() => new Observable<null>(() => undefined));
    const element = await create();
    typeIn(element, 'a-good-password');
    submit(element);
    expect(element.querySelector('[data-cy="stateLoading"]')).not.toBeNull();
    expect(element.querySelector('form')).toBeNull();
    expect(element.textContent).toContain('Setting your new password');
  });

  it('does not tell somebody with a reset link that the MARKETPLACE is down', async () => {
    // The same control `activation.spec.ts` carries, for the same reason — D105 §6. `LoadState`'s
    // defaults name a catalogue, and nobody arriving from a password-reset mail asked about one.
    accounts.finishPasswordReset = vitest.fn(() => new Observable<null>(() => undefined));
    const element = await create();
    typeIn(element, 'a-good-password');
    submit(element);
    const text = element.textContent;
    expect(text).not.toContain('Loading the marketplace');
    expect(text).not.toContain('listings from the catalogue');
  });
});
