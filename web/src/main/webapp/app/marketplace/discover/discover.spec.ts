import { beforeEach, describe, expect, it, vitest } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { CATEGORIES, FACETS, P12_CARD, P1_CARD, loadEnglish, pageOf } from '../marketplace.fixtures';
import { MarketplaceService } from '../marketplace.service';

import Discover from './discover';

describe('Discover', () => {
  let fixture: ComponentFixture<Discover>;
  let marketplace: {
    categories: ReturnType<typeof vitest.fn>;
    browse: ReturnType<typeof vitest.fn>;
    facets: ReturnType<typeof vitest.fn>;
    reviewCount: ReturnType<typeof vitest.fn>;
  };

  const create = async (): Promise<void> => {
    await TestBed.configureTestingModule({
      imports: [Discover],
      providers: [provideRouter([]), provideTranslateService(), { provide: MarketplaceService, useValue: marketplace }],
    }).compileComponents();
    // THE REAL TRANSLATION BUNDLE, so every assertion over `textContent` below reads the prose a
    // visitor reads rather than the translation KEY. Without it `abmTranslate` renders
    // `marketplace.price.free` and a template's fallback text is never in the DOM at all — which is
    // what made the commission-rate guard below reach nothing. See `terms-are-not-quoted.spec.ts`.
    loadEnglish();
    fixture = TestBed.createComponent(Discover);
    fixture.detectChanges();
  };

  beforeEach(() => {
    marketplace = {
      categories: vitest.fn(() => of(CATEGORIES)),
      browse: vitest.fn(() => of(pageOf([P1_CARD, P12_CARD]))),
      facets: vitest.fn(() => of(FACETS)),
      reviewCount: vitest.fn(() => of(73)),
    };
  });

  it('shows only figures the estate answered with', async () => {
    // THE DEFECT THIS SCREEN EXISTS NOT TO REPEAT. The prototype's "Sessions brokered" tile summed
    // two in-memory arrays and read a plausible 269 while the estate seeds 256. Every figure here
    // is a read, and the fourth tile is omitted — nothing this estate publishes counts bookings for
    // an anonymous reader, which is the same answer the prototype's own live block reaches.
    await create();
    const element = fixture.nativeElement as HTMLElement;
    const stats = element.querySelector('[data-cy="heroStats"]');
    expect(stats).not.toBeNull();
    const text = stats?.textContent ?? '';
    expect(text).toContain('18');
    expect(text).toContain('73');
    expect(text.toLowerCase()).not.toContain('session');
  });

  it('asks the server for the verified count rather than counting a page of cards', async () => {
    // Counting `content.filter(verified)` would report "2 verified" from a four-card featured strip.
    await create();
    expect(marketplace.browse).toHaveBeenCalledWith({ verifiedOnly: true, size: 1 });
  });

  it('asks for the featured strip by the recommended order', async () => {
    // "Ranked by rating weighted for review volume" is `recommended` on the server — rating first,
    // then volume. `rating` alone is a different order and would make the lede false.
    await create();
    expect(marketplace.browse).toHaveBeenCalledWith({ sort: 'recommended', size: 4 });
  });

  it('renders a failure with a retry rather than a spinner that never stops', async () => {
    // The state the prototype has no markup for. `@if (loaded) {…} @else { spinner }` is
    // indistinguishable from a slow network for as long as somebody is willing to wait.
    marketplace.browse = vitest.fn(() => throwError(() => new Error('down')));
    await create();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-cy="stateFailed"]')).not.toBeNull();
    expect(element.querySelector('[data-cy="stateRetry"]')).not.toBeNull();
  });

  it('lets no subscription rethrow when the estate is down', async () => {
    // A `subscribe({ next })` with no `error` arm RETHROWS, and RxJS reports it as an unhandled
    // error — in a browser, an uncaught exception on a page whose own failure state is handled and
    // rendering correctly. It was measured here before the arms were added: three "Vitest caught
    // unhandled errors" reports with every assertion in this file green, which is exactly the shape
    // that survives review. `onunhandledrejection` does not see it, so it is asserted structurally.
    marketplace.browse = vitest.fn(() => throwError(() => new Error('down')));
    marketplace.facets = vitest.fn(() => throwError(() => new Error('down')));
    marketplace.categories = vitest.fn(() => throwError(() => new Error('down')));
    marketplace.reviewCount = vitest.fn(() => throwError(() => new Error('down')));
    await expect(create()).resolves.toBeUndefined();
    expect(fixture.componentInstance.reviewTotal()).toBeNull();
    expect(fixture.componentInstance.verifiedTotal()).toBeNull();
  });

  it('retries on demand', async () => {
    marketplace.browse = vitest.fn(() => throwError(() => new Error('down')));
    await create();
    const before = marketplace.browse.mock.calls.length;
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-cy="stateRetry"]')?.click();
    fixture.detectChanges();
    expect(marketplace.browse.mock.calls.length).toBeGreaterThan(before);
  });

  it('renders an empty catalogue as an ordinary answer, not as an error', async () => {
    marketplace.browse = vitest.fn(() => of(pageOf([])));
    marketplace.categories = vitest.fn(() => of([]));
    await create();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('[data-cy="stateEmpty"]').length).toBeGreaterThan(0);
    expect(element.querySelector('[data-cy="stateFailed"]')).toBeNull();
  });

  it('hands Browse its query in the URL so a search is a link', async () => {
    await create();
    const router = TestBed.inject(Router);
    const navigate = vitest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.search.setValue({ q: '  massage  ', city: 'Accra', mode: '' });
    fixture.componentInstance.submitSearch();
    expect(navigate).toHaveBeenCalledWith(['/browse'], { queryParams: { q: 'massage', city: 'Accra' } });
  });

  it('renders the scope note, which is a boundary rather than copy', async () => {
    // hc-market/CLAUDE.md: everyone listed is non-medical and may not diagnose or prescribe.
    await create();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-cy="scopeNote"]')).not.toBeNull();
  });

  it('renders the four brokerage steps as prose rather than as translation keys', async () => {
    // THE CONTROL FOR EVERY ASSERTION OVER RENDERED TEXT IN THIS FILE, and it is the one thing the
    // previous version of the guard below lacked: with no bundle loaded `abmTranslate` renders the
    // KEY, so "the DOM does not contain 12%" was true of a DOM containing no sentences at all.
    //
    // The prefix is `marketplace.` and NOT `marketplace.discover.`, which is what it was until D103
    // §14: this screen renders `marketplace.scope.*`, `marketplace.mode.*`, `marketplace.price.*` and
    // `marketplace.verification.*` as well, so the narrow prefix left a deleted `marketplace.scope.body`
    // — the one piece of prose CLAUDE.md calls a hard boundary rather than copy — rendering its own key
    // to a visitor with every test green. Measured. `professional-card.spec.ts` already had this shape.
    await create();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text, 'the bundle did not load — every text assertion here is vacuous').not.toContain('marketplace.');
    expect(text).toContain('Four steps, and money only moves after the session happens.');
    expect(text).toContain('The fee is held and released after the session');
  });

  it('names no commission rate and no cancellation window, in the text a visitor reads', async () => {
    // Both are configurable per estate (D57) and no endpoint publishes them without a token, so a
    // "12%" compiled in here would go wrong silently on the first estate priced differently.
    // Backlog NEW-83 is where a public terms endpoint would come from.
    //
    // This asserts over the RENDERED text with the real bundle loaded, so it now sees a number typed
    // into `i18n/en/marketplace.json`. It still cannot see one typed into a template's `abmTranslate`
    // FALLBACK — that text is replaced by `innerHTML` and never reaches any DOM — which is why
    // `terms-are-not-quoted.spec.ts` reads the files as well. Two halves, two reasons to fail.
    await create();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).not.toMatch(/\d+\s*(%|per cent)[^.]{0,40}(brokerage|commission|fee|platform)/i);
    expect(text).not.toMatch(/(brokerage|commission|fee|platform)[^.]{0,40}\d+\s*(%|per cent)/i);
    expect(text).not.toMatch(/\d+\s*hours?[^.]{0,60}(cancel|refund)/i);
    expect(text).not.toMatch(/(cancel|refund)\w*[^.]{0,60}\d+\s*hours?/i);
  });
});
