import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { provideTranslateService } from '@ngx-translate/core';

import { ALL_FREE_CARD, NOTHING_PUBLISHED_CARD, P12_CARD, P1_CARD, UNRATED_CARD, loadEnglish } from '../marketplace.fixtures';
import { ProfessionalCard } from '../marketplace.model';

import ProfessionalCardComponent from './professional-card';

/**
 * **This file exists because of D100, and its first case is the one that matters.**
 *
 * <p>D100 gave `ProfessionalCard` three price quantities. A card whose headline renders
 * `fromPriceMinor` says "from ₵0" for a professional whose cheapest purchasable service is ₵280 —
 * the misrepresentation D100 closed one layer down, rebuilt one layer up — and it is also the
 * quantity the server's `maxPriceMinor` filter does *not* read, so the slider and the headline
 * would describe different catalogues.
 */
describe('ProfessionalCard', () => {
  let fixture: ComponentFixture<ProfessionalCardComponent>;

  const render = (card: ProfessionalCard): HTMLElement => {
    fixture.componentRef.setInput('card', card);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfessionalCardComponent],
      providers: [provideRouter([]), provideTranslateService()],
    }).compileComponents();
    // The real bundle, so the assertions below read "Free" and "Price on enquiry" rather than
    // `marketplace.price.free`. Without it every `abmTranslate` renders its key, and an assertion
    // that the text is not `Infinity` is satisfied by an EMPTY element — which is exactly what the
    // first version of the two cases below could not tell apart. See `terms-are-not-quoted.spec.ts`.
    loadEnglish();
    fixture = TestBed.createComponent(ProfessionalCardComponent);
  });

  it('renders the cheapest PURCHASABLE price as the headline, not the literal minimum', () => {
    // p12 publishes a free "Discovery session" alongside a ₵280 one, so `fromPriceMinor` is 0.
    // Measured on the quality estate 2026-09-24.
    expect(P12_CARD.fromPriceMinor).toBe(0);
    const element = render(P12_CARD);
    expect(fixture.componentInstance.priceState()).toBe('paid');
    expect(fixture.componentInstance.fromPrice()).toBe('₵280');
    expect(element.textContent).toContain('₵280');
    expect(element.textContent).not.toContain('₵0');
  });

  it('marks a free service beside the price rather than inside it', () => {
    // D92 §4 ratified a marker in place of a ₵0 headline; D100 §3 rejected naming the service,
    // because the two free ones in the seed are "Discovery session" and "Doula consultation" and
    // neither is an intro.
    const element = render(P12_CARD);
    expect(element.querySelector('[data-cy="freeServiceMarker"]')).not.toBeNull();
    expect(render(P1_CARD).querySelector('[data-cy="freeServiceMarker"]')).toBeNull();
  });

  it('RENDERS an all-free listing as "Free" and an empty one as "Price on enquiry"', () => {
    // NEITHER HAS EVER EXISTED IN EITHER ESTATE, which is exactly why they are tested — and why the
    // assertion has to be over OUTPUT. The first version of this case asserted `priceState()`, a
    // computed, so blanking either `@case` body in the template left it green: an all-free listing
    // rendered an EMPTY price box and 267 tests passed. Measured, both arms.
    //
    // `Free` is NEW-78's answer: the prototype renders `money(Math.min.apply(null, []))` here, and
    // `Math.min` of nothing is `Infinity`. Not ₵0 either — that is the collapse D100 closed.
    const free = render(ALL_FREE_CARD);
    expect(fixture.componentInstance.priceState()).toBe('free');
    expect(free.querySelector('.abm-card__amount')?.textContent.trim()).toBe('Free');

    const enquire = render(NOTHING_PUBLISHED_CARD);
    expect(fixture.componentInstance.priceState()).toBe('enquire');
    expect(enquire.querySelector('.abm-card__amount')?.textContent.trim()).toBe('Price on enquiry');
  });

  it('renders a non-empty price for every one of the four states', () => {
    // The mechanical half of the same property, over all four rows of D103 §2's table at once: a
    // blank price box is the failure a `not.toMatch(/Infinity|NaN/)` assertion cannot see, because
    // nothing matches nothing.
    for (const card of [P1_CARD, P12_CARD, ALL_FREE_CARD, NOTHING_PUBLISHED_CARD]) {
      const amount = render(card).querySelector('.abm-card__amount')?.textContent.trim() ?? '';
      expect(amount, `${card.ref} renders an empty price`).not.toBe('');
      expect(amount, `${card.ref} renders a translation key`).not.toContain('marketplace.');
    }
  });

  it('never renders Infinity or NaN for any of the four price states', () => {
    // A NEGATIVE assertion, and negative assertions are satisfied by emptiness — which is why the
    // two positive cases above exist beside it. Kept because the defect it names is real: a card
    // rendering ₵NaN passed every automated check in this repository once already, on the prototype.
    for (const card of [P1_CARD, P12_CARD, ALL_FREE_CARD, NOTHING_PUBLISHED_CARD]) {
      const text = render(card).textContent;
      expect(text, `${card.ref} renders a non-number`).not.toMatch(/Infinity|NaN|undefined|null/);
    }
  });

  it('says "no reviews yet" for an unrated professional rather than drawing five empty stars', () => {
    // `rating` is null and never 0 — the `professional_rating` view has no row. Five empty stars
    // reads as the worst rating on the marketplace, which is the collapse CLAUDE.md forbids.
    const element = render(UNRATED_CARD);
    expect(element.textContent).not.toContain('0.0');
    expect(element.querySelectorAll('.abm-stars__glyph')).toHaveLength(0);
  });

  it('is one keyboard-reachable link to the public profile', () => {
    // The prototype makes the whole <article> clickable with an onclick, which no keyboard reaches
    // and which has no address to copy.
    const anchor = render(P1_CARD).querySelector('a');
    expect(anchor?.getAttribute('href')).toBe('/professionals/p1');
  });

  it('carries no booking, message or save control', () => {
    // All three need a token and belong to Stage C. A control that does nothing is worse than none.
    const element = render(P1_CARD);
    expect(element.querySelectorAll('button')).toHaveLength(0);
  });
});
