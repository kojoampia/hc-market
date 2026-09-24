import { beforeEach, describe, expect, it, vitest } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { provideTranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { P1_DETAIL, REVIEWS, loadEnglish, pageOf } from '../marketplace.fixtures';
import { MarketplaceService } from '../marketplace.service';

import Professional from './professional';

describe('Professional', () => {
  let fixture: ComponentFixture<Professional>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let marketplace: {
    professional: ReturnType<typeof vitest.fn>;
    availability: ReturnType<typeof vitest.fn>;
    reviews: ReturnType<typeof vitest.fn>;
  };

  const create = async (): Promise<void> => {
    await TestBed.configureTestingModule({
      imports: [Professional],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: MarketplaceService, useValue: marketplace },
        { provide: ActivatedRoute, useValue: { paramMap: params.asObservable() } },
      ],
    }).compileComponents();
    // The real bundle — see `discover.spec.ts` and `terms-are-not-quoted.spec.ts`. Every assertion
    // over `textContent` in this file is vacuous without it.
    loadEnglish();
    fixture = TestBed.createComponent(Professional);
    fixture.detectChanges();
  };

  beforeEach(() => {
    params = new BehaviorSubject(convertToParamMap({ ref: 'p1' }));
    marketplace = {
      professional: vitest.fn(() => of(P1_DETAIL)),
      availability: vitest.fn(() => of([{ date: '2026-08-10', slots: ['08:30', '10:00'] }])),
      reviews: vitest.fn(() => of(pageOf(REVIEWS, 0, 4))),
    };
  });

  it('reads the star distribution ONE STAR FIRST and draws it five down', async () => {
    // `[10, 0, 0, 2, 5]` on p1 averages (10·1 + 2·4 + 5·5)/17 = 2.53 against the 2.5 the
    // `professional_rating` view reports — which is how the order was ESTABLISHED rather than
    // assumed. Reversed, the same array would say p1 has ten 5★ reviews and a 2.5 average.
    await create();
    expect(fixture.componentInstance.distribution()).toEqual([
      { stars: 5, count: 5 },
      { stars: 4, count: 2 },
      { stars: 3, count: 0 },
      { stars: 2, count: 0 },
      { stars: 1, count: 10 },
    ]);
  });

  it('renders the headline from the paid floor and the per-service prices as they are', async () => {
    // One quantity, one rendering, three screens (D100). "Free" belongs on a service that costs
    // nothing; it never becomes the LISTING's headline.
    await create();
    expect(fixture.componentInstance.fromPrice()).toBe('₵150');
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('₵150');
    expect(text).toContain('₵280');
    expect((fixture.nativeElement as HTMLElement).querySelector('.abm-pro__price')?.textContent.trim()).toBe('₵150');
  });

  it("RENDERS the profile's two null-price states rather than leaving the panel blank", async () => {
    // The profile's own copy of the card's four states, asserted over OUTPUT for the same reason:
    // blanking BOTH `@case` bodies here left 267 tests green, because the only assertions were over
    // `priceState()` and over a regex that emptiness satisfies. Measured.
    marketplace.professional = vitest.fn(() =>
      of({ ...P1_DETAIL, card: { ...P1_DETAIL.card, fromPaidPriceMinor: null, hasFreeService: true } }),
    );
    await create();
    expect(fixture.componentInstance.priceState()).toBe('free');
    expect((fixture.nativeElement as HTMLElement).querySelector('.abm-pro__price')?.textContent.trim()).toBe('Free');

    marketplace.professional = vitest.fn(() =>
      of({ ...P1_DETAIL, card: { ...P1_DETAIL.card, fromPaidPriceMinor: null, hasFreeService: false } }),
    );
    TestBed.resetTestingModule();
    await create();
    expect(fixture.componentInstance.priceState()).toBe('enquire');
    expect((fixture.nativeElement as HTMLElement).querySelector('.abm-pro__price')?.textContent.trim()).toBe('Price on enquiry');
  });

  it('treats an empty-string reply as no reply', async () => {
    // The seed answers `""` and a review written through the API answers `null`. A template testing
    // only for `null` renders an empty reply block on all 63 seeded reviews.
    await create();
    expect(fixture.componentInstance.hasReply(REVIEWS[0])).toBe(false);
    expect(fixture.componentInstance.hasReply(REVIEWS[1])).toBe(true);
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.abm-review__reply')).toHaveLength(1);
  });

  it('says so when the professional has published no openings', async () => {
    // THE STATE THE QUALITY ESTATE IS IN TODAY. Measured 2026-09-24: the seed's slots are anchored
    // at 2026-08-10 and the endpoint's default ten-day window starts from the marketplace's today,
    // so `/availability` answers `[]` for every professional. Rendering nothing would read as a
    // broken panel. Backlog NEW-84.
    marketplace.availability = vitest.fn(() => of([]));
    await create();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-cy="availability"]')).toBeNull();
    expect(element.querySelectorAll('[data-cy="stateEmpty"]').length).toBeGreaterThan(0);
  });

  it('treats ten days of empty slot lists as no openings too', async () => {
    // The same sentence from a different answer shape: a day row with no slots is not an opening.
    marketplace.availability = vitest.fn(() => of([{ date: '2026-09-24', slots: [] }]));
    await create();
    expect(fixture.componentInstance.availabilityState()).toBe('empty');
  });

  it('omits the verification date when the audit trail records none', async () => {
    // `verifiedOn` is null on all eighteen seeded professionals, so the badge sentence must stand
    // without a date rather than rendering "on null" — D33, and the prototype's own behaviour.
    await create();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).not.toContain('null');
    expect(text).not.toContain('undefined');
  });

  it('carries no booking, message or save control', async () => {
    // All three need a token and belong to Stage C. The only buttons on this screen are the review
    // pager's, and with two reviews on one page there are none.
    await create();
    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    expect(buttons.map(button => button.textContent.trim())).toEqual([]);
  });

  it('answers a missing professional and an unreachable catalogue identically', async () => {
    // A 404 and a 502 both mean "there is nothing at this address" to a visitor, and a public page
    // must not tell a stranger which of the two it is.
    marketplace.professional = vitest.fn(() => throwError(() => new Error('404')));
    await create();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-cy="stateFailed"]')).not.toBeNull();
  });

  it('re-reads everything when the route parameter changes', async () => {
    // Angular reuses a component across a parameter change, so a `ngOnInit`-only read leaves the
    // previous professional on screen at the new address.
    await create();
    params.next(convertToParamMap({ ref: 'p12' }));
    fixture.detectChanges();
    expect(marketplace.professional).toHaveBeenLastCalledWith('p12');
    expect(marketplace.availability).toHaveBeenLastCalledWith('p12');
    expect(marketplace.reviews).toHaveBeenLastCalledWith('p12', 0, 4);
  });

  it('renders the scope note on the profile too', async () => {
    // This is the screen where somebody decides who comes into their home.
    await create();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-cy="scopeNote"]')).not.toBeNull();
  });

  it('renders its panels as prose rather than as translation keys', async () => {
    // The control, as in `discover.spec.ts`: with no bundle every `abmTranslate` renders its key and
    // an assertion that the DOM lacks a number is true of a DOM with no sentences in it. The prefix is
    // the bare `marketplace.` since D103 §14 — this screen renders `marketplace.scope.*` and
    // `marketplace.verification.*` too, and the narrower one could not see either go missing.
    await create();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text, 'the bundle did not load — every text assertion here is vacuous').not.toContain('marketplace.');
    expect(text).toContain('The brokerage fee is included in these prices.');
    expect(text).toContain('The fee is held until the session is complete.');
  });

  it('names no commission rate and no cancellation window, in the text a visitor reads', async () => {
    // Same reason as Discover: both are configurable per estate (D57) and nothing public publishes
    // them. Backlog NEW-83. Asserted over rendered text with the real bundle loaded; the template
    // fallbacks are covered by `terms-are-not-quoted.spec.ts`, which reads the files.
    //
    // NOTE the profile legitimately renders a rebook percentage — `rebookRatePct`, a fact about ONE
    // listing that the API publishes (68 on this fixture, 88 on p13 live) — so a bare /\d+%/ here
    // would be red on correct code, and this assertion is what stops the ban being widened into one.
    await create();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text, 'the professional-specific percentage must survive').toContain('68% rebook');
    expect(text).not.toMatch(/\d+\s*(%|per cent)[^.]{0,40}(brokerage|commission|fee|platform)/i);
    expect(text).not.toMatch(/(brokerage|commission|fee|platform)[^.]{0,40}\d+\s*(%|per cent)/i);
    expect(text).not.toMatch(/\d+\s*hours?[^.]{0,60}(cancel|refund)/i);
    expect(text).not.toMatch(/(cancel|refund)\w*[^.]{0,60}\d+\s*hours?/i);
  });
});
