import { beforeEach, describe, expect, it, vitest } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { provideTranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { loadEnglish } from 'app/marketplace/marketplace.fixtures';

import { AccountLifecycleService } from '../account-lifecycle.service';

import Activation from './activation';

/**
 * **`/account/activate?key=…` — every outcome a real key can produce.**
 *
 * <p>The statuses asserted against are measured, not invented: against the quality gateway on
 * 2026-10-02, `GET /api/activate?key=zzz-not-a-key` answered **400** with *"No user was found for
 * this activation key"*, and `GET /api/activate` with no key at all answered **400** with
 * *"Required query parameter 'key' is not present"*. The second one is the case this screen must
 * never produce — see `does not ask the server about a key it does not have`.
 */
describe('Activation', () => {
  let fixture: ComponentFixture<Activation>;
  let accounts: { activate: ReturnType<typeof vitest.fn> };
  let queryParams: Record<string, string>;

  const create = async (): Promise<HTMLElement> => {
    await TestBed.configureTestingModule({
      imports: [Activation],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: AccountLifecycleService, useValue: accounts },
        {
          // The real `ActivatedRoute` needs a navigation; the screen only ever reads `queryParamMap`,
          // so this is the narrowest stub that exercises the code path. `convertToParamMap` is not
          // imported because the component reads through `paramValue`, which takes a `ParamMap` —
          // and the shape below IS one.
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
    // THE REAL BUNDLE. `TranslateDirective` sets `innerHTML` from the translation, so with no bundle
    // loaded ngx-translate returns the KEY and a template's fallback prose is never in the DOM at
    // all — which is what made D103's first commission-rate guard reach nothing. Any assertion over
    // rendered TEXT below is reading prose only because of this line.
    loadEnglish();
    fixture = TestBed.createComponent(Activation);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(() => {
    queryParams = { key: 'a-real-looking-key' };
    accounts = { activate: vitest.fn(() => of(null)) };
  });

  it('activates with the key out of the query string, and says so', async () => {
    const element = await create();
    expect(accounts.activate).toHaveBeenCalledWith('a-real-looking-key');
    expect(element.querySelector('[data-cy="activateSuccess"]')).not.toBeNull();
    expect(element.querySelector('[data-cy="activateSignIn"]')).not.toBeNull();
    // The prose, which is only here because `loadEnglish()` ran.
    expect(element.textContent).toContain('has been activated');
  });

  it('offers a route to sign in, which is the only thing left to do', async () => {
    const element = await create();
    const link = element.querySelector('[data-cy="activateSignIn"]');
    expect(link?.getAttribute('href')).toBe('/login');
  });

  it('renders a NAMED failure for a key the estate rejects, not a spinner', async () => {
    // The 400 measured above. "A spinner for ever" is what a two-state template does on every
    // failure and is indistinguishable from a slow network for as long as somebody will wait.
    accounts.activate = vitest.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 400, error: { detail: 'No user was found for this activation key' } })),
    );
    const element = await create();
    expect(element.querySelector('[data-cy="stateFailed"]')).not.toBeNull();
    expect(element.querySelector('[data-cy="stateLoading"]')).toBeNull();
    expect(element.querySelector('[data-cy="activateSuccess"]')).toBeNull();
    expect(element.querySelector('[data-cy="stateRetry"]')).not.toBeNull();
  });

  it('says NOTHING about which failure it was, and shows no status code', async () => {
    // A page that distinguishes its refusals is an oracle, and "this account is already activated"
    // is one of the two readings of that 400. The estate's own words are not relayed either — the
    // detail goes nowhere near the DOM.
    accounts.activate = vitest.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 400, error: { detail: 'No user was found for this activation key' } })),
    );
    const element = await create();
    const text = element.textContent;
    expect(text).not.toContain('400');
    expect(text).not.toContain('No user was found');
    // …and the control for that pair of negatives: the screen's OWN words are present, so this is
    // not passing over an empty DOM. The sentence is the generated bundle's `activate.messages.error`.
    expect(text).toContain('use the registration form');
  });

  it('renders the SAME failure for an unreachable estate as for a rejected key', async () => {
    // Deliberate, and the same call `professional.ts` makes for a 404 against a 502.
    accounts.activate = vitest.fn(() => throwError(() => new HttpErrorResponse({ status: 0 })));
    const unreachable = (await create()).innerHTML;
    accounts.activate = vitest.fn(() => throwError(() => new HttpErrorResponse({ status: 400 })));
    TestBed.resetTestingModule();
    expect((await create()).innerHTML).toBe(unreachable);
  });

  it('lets the subscription NOT rethrow when the estate is down', async () => {
    // A `subscribe({ next })` with no `error` arm RETHROWS and RxJS reports it as an unhandled
    // error — in a browser, an uncaught exception on a page whose own failure state renders
    // correctly. It cost three of those in Stage B with every assertion green, so this asserts the
    // rendered failure state rather than that nothing threw: `drive()` is what carries the arm.
    accounts.activate = vitest.fn(() => throwError(() => new Error('down')));
    const element = await create();
    expect(element.querySelector('[data-cy="stateFailed"]')).not.toBeNull();
  });

  it('retries with the same key, because the usual failure is a restart', async () => {
    accounts.activate = vitest.fn(() => throwError(() => new HttpErrorResponse({ status: 503 })));
    const element = await create();
    (element.querySelector('[data-cy="stateRetry"]') as HTMLButtonElement).click();
    expect(accounts.activate).toHaveBeenCalledTimes(2);
    expect(accounts.activate).toHaveBeenLastCalledWith('a-real-looking-key');
  });

  it('does not ask the server about a key it does not have', async () => {
    // ⚠ THE CASE THE ITEM NAMES. `GET /api/activate` with no key answers 400 with "Required query
    // parameter 'key' is not present" — measured — which this screen would then report as a failed
    // activation when nothing was ever attempted. A missing key is a DECIDED state and makes no
    // request at all.
    queryParams = {};
    const element = await create();
    expect(accounts.activate).not.toHaveBeenCalled();
    expect(element.querySelector('[data-cy="stateEmpty"]')).not.toBeNull();
    expect(element.querySelector('[data-cy="stateFailed"]')).toBeNull();
    expect(element.querySelector('[data-cy="stateLoading"]')).toBeNull();
    expect(element.textContent).toContain('activation key is missing');
  });

  it('treats `?key=` as no key at all, rather than sending an empty one', async () => {
    // `paramValue` collapses `''` to `undefined`. An empty key is not a key, and the server would
    // answer 400 for it — the same wrong report as above.
    queryParams = { key: '   ' };
    const element = await create();
    expect(accounts.activate).not.toHaveBeenCalled();
    expect(element.querySelector('[data-cy="stateEmpty"]')).not.toBeNull();
  });

  it('renders the loading state while the request is in flight', async () => {
    // Never completes. The third of the three states, and the one a two-state template collapses
    // into the failure.
    accounts.activate = vitest.fn(() => new Observable<null>(() => undefined));
    const element = await create();
    expect(element.querySelector('[data-cy="stateLoading"]')).not.toBeNull();
    expect(element.querySelector('[data-cy="activateSuccess"]')).toBeNull();
  });

  it('does not tell somebody with an activation link that the MARKETPLACE is down', async () => {
    // THE REASON `LoadState` GREW OVERRIDABLE KEYS (D105 §6). Its defaults are "The marketplace
    // could not be reached / The catalogue did not answer", which are right on all three public
    // screens and wrong here: somebody who clicked a link in a mail message has not asked about a
    // catalogue, and pointing them at one points them at the wrong thing. This is the control that
    // makes the override more than a parameter nobody passes.
    accounts.activate = vitest.fn(() => throwError(() => new HttpErrorResponse({ status: 400 })));
    const text = (await create()).textContent;
    expect(text).not.toContain('marketplace could not be reached');
    expect(text).not.toContain('catalogue did not answer');
    expect(text).toContain('could not be activated');
  });
});
