import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { TranslatePipe } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';

import Stars from '../stars/stars';
import { ProfessionalCard } from '../marketplace.model';
import { money } from '../money';

/**
 * One listing, as Discover and Browse render it.
 *
 * <p><b>THE HEADLINE PRICE IS `fromPaidPriceMinor`, AND THAT IS THE DECISION — `decisions.md` D100
 * and D103 §3.</b> `fromPriceMinor` is the literal minimum including a free service, so the two
 * professionals who publish one (p12, p13) report `0` there while their cheapest purchasable
 * service is ₵280 and ₵420. A card whose headline read `fromPriceMinor` would say "from ₵0" for a
 * doula whose packages run to ₵3,200 — <b>which is exactly the defect D100 closed one layer down,
 * rebuilt one layer up</b>, and it is also the quantity the server's `maxPriceMinor` filter does
 * NOT read, so the slider and the headline would describe different catalogues.
 *
 * <p><b>The free service is a marker beside the price and never the price.</b> It says
 * "Free service available" and not "free intro": D100 §3 rejected naming it, because the two free
 * services in the seed are "Discovery session" and "Doula consultation" and neither is an intro,
 * and because a service name is free text its own professional types (NEW-58).
 *
 * <p>Four price states, and all four are reachable from the API:
 *
 * <table>
 *   <tr><th>`fromPaidPriceMinor`</th><th>`hasFreeService`</th><th>renders</th><th>seen</th></tr>
 *   <tr><td>a number</td><td>false</td><td>"from ₵150"</td><td>16 of 18</td></tr>
 *   <tr><td>a number</td><td>true</td><td>"from ₵280" + the marker</td><td>p12, p13</td></tr>
 *   <tr><td>null</td><td>true</td><td>"Free" + the marker</td><td>never — no such listing</td></tr>
 *   <tr><td>null</td><td>false</td><td>"Price on enquiry"</td><td>never — no such listing</td></tr>
 * </table>
 *
 * <p>The last two have never existed in either estate, which is precisely why they are written down
 * and tested: `Math.min.apply(null, [])` is `Infinity`, so the prototype renders <b>₵Infinity</b> on
 * the all-free edge under a comment claiming it handles that edge (backlog NEW-78). The answer that
 * item defers to this card design is the third row — <b>"Free", not ₵0 and not ₵∞</b>.
 *
 * <p><b>There is no save, book or message control here.</b> All three need a token and belong to
 * Stage C; a button that does nothing is worse than no button, and this stage has no token anywhere.
 */
@Component({
  selector: 'abm-professional-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './professional-card.html',
  styleUrl: './professional-card.scss',
  imports: [RouterLink, TranslateDirective, TranslatePipe, Stars],
})
export default class ProfessionalCardComponent {
  readonly card = input.required<ProfessionalCard>();

  readonly verified = computed(() => this.card().verification === 'VERIFIED');

  /** The headline, already formatted. `null` means "there is no purchasable price" — see below. */
  readonly fromPrice = computed(() => money(this.card().fromPaidPriceMinor, this.card().currency));

  /**
   * `paid` | `free` | `enquire`. A template cannot branch on three states through two `@if`s
   * without one of them meaning "whatever is left", which is how the all-free listing became
   * "Price on enquiry" in the first draft of this component.
   */
  readonly priceState = computed<'paid' | 'free' | 'enquire'>(() => {
    if (this.card().fromPaidPriceMinor !== null) return 'paid';
    return this.card().hasFreeService ? 'free' : 'enquire';
  });

  readonly gradient = computed(() => `linear-gradient(150deg, ${this.card().avatarGradientFrom}, ${this.card().avatarGradientTo})`);
}
