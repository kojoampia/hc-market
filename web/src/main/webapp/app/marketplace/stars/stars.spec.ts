import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideTranslateService } from '@ngx-translate/core';

import Stars from './stars';

describe('Stars', () => {
  let fixture: ComponentFixture<Stars>;

  const withRating = (rating: number | null, reviewCount: number | null = null): HTMLElement => {
    fixture.componentRef.setInput('rating', rating);
    fixture.componentRef.setInput('reviewCount', reviewCount);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Stars],
      providers: [provideTranslateService()],
    }).compileComponents();
    fixture = TestBed.createComponent(Stars);
  });

  it('draws no stars at all for a null rating', () => {
    // THE CENTRAL RULE. `professional_rating` has no row for an unreviewed professional, so `rating`
    // is null and never 0.0 — "unrated" and "rated badly" must not collapse. `Math.round(null)` is
    // 0, which draws five empty stars and reads as the worst listing on the marketplace.
    const element = withRating(null);
    expect(element.querySelectorAll('.abm-stars__glyph')).toHaveLength(0);
    expect(element.querySelector('.abm-stars--unrated')).not.toBeNull();
  });

  it('fills to the nearest star, which is what the prototype does', () => {
    withRating(4.8);
    expect(fixture.componentInstance.glyphs()).toEqual([true, true, true, true, true]);
    withRating(2.5);
    expect(fixture.componentInstance.glyphs()).toEqual([true, true, true, false, false]);
    withRating(0);
    expect(fixture.componentInstance.glyphs()).toEqual([false, false, false, false, false]);
  });

  it('treats a genuine zero rating as rated', () => {
    // The distinction the null case rests on, asserted from the other side: a 0.0 the view actually
    // produced is a rating, and hiding it would be the same collapse in the opposite direction.
    withRating(0);
    expect(fixture.componentInstance.rated()).toBe(true);
    expect(fixture.componentInstance.formatted()).toBe('0.0');
  });

  it('gives a screen reader the number rather than five similar characters', () => {
    const element = withRating(4.8, 4);
    expect(element.querySelector('.abm-stars__glyphs')?.getAttribute('aria-hidden')).toBe('true');
    expect(element.querySelector('.visually-hidden')).not.toBeNull();
  });

  it('omits the review count when it was not given', () => {
    // The review list renders one row of stars per review, where a count would be meaningless.
    expect(withRating(5, null).querySelector('.abm-stars__count')).toBeNull();
    expect(withRating(5, 17).querySelector('.abm-stars__count')).not.toBeNull();
  });
});
