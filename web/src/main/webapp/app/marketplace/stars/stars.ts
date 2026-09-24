import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TranslateDirective } from 'app/shared/language';

/**
 * A rating, or an honest statement that there is not one.
 *
 * <p><b>`rating` is `null` and never `0.0` for a professional with no reviews</b> — the
 * `professional_rating` view simply has no row for them, which is `hc-market/CLAUDE.md`'s central
 * rule stated as data: <i>"unrated" and "rated badly" must not collapse into the same number</i>.
 * So a null rating renders the words "No reviews yet" and <b>not</b> five empty stars, which is what
 * `Math.round(null)` gives you and which reads as the worst rating on the marketplace.
 *
 * <p>The glyphs are decorative and carry `aria-hidden`; the accessible name is the number, because
 * five characters that differ only in whether they are filled are not a rating to a screen reader.
 */
@Component({
  selector: 'abm-stars',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stars.html',
  styleUrl: './stars.scss',
  imports: [TranslateDirective],
})
export default class Stars {
  readonly rating = input<number | null>(null);
  readonly reviewCount = input<number | null>(null);
  /** Hide the numeric value — the review list renders one row of stars per review. */
  readonly showValue = input(true);

  readonly rated = computed(() => this.rating() !== null);

  /** `[true, true, true, false, false]` — filled to the NEAREST star, which is the prototype's. */
  readonly glyphs = computed<boolean[]>(() => {
    const value = this.rating();
    if (value === null) return [];
    const filled = Math.round(value);
    return [1, 2, 3, 4, 5].map(position => position <= filled);
  });

  readonly formatted = computed(() => {
    const value = this.rating();
    return value === null ? '' : value.toFixed(1);
  });
}
