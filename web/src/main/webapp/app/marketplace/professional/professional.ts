import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { TranslatePipe } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';

import LoadState from '../load-state/load-state';
import Stars from '../stars/stars';
import { AvailabilityDay, Page, ProfessionalDetail, ReviewView } from '../marketplace.model';
import { drive, loadableSignal } from '../load-state';
import { MarketplaceService } from '../marketplace.service';
import { money } from '../money';
import { paramValue } from '../route-params';

/** The prototype's review page size. */
const REVIEWS_PER_PAGE = 4;

/**
 * The public profile — `/professionals/:ref`.
 *
 * <p><b>The namespace is `/professionals/:ref` and not `/pro/:ref`</b>, which the backlog asks for
 * explicitly: the prototype's router tells `#/pro/p1` from `#/pro/profile` with a reserved-word list
 * duplicated at two sites, so a professional whose reference happened to be `profile` was
 * unreachable. Splitting the namespaces deletes the list and the latent defect together; `/pro/*`
 * stays free for Stage D's workspace.
 *
 * <p><b>There is no booking, messaging or save control on this screen.</b> All three need a token
 * and belong to Stage C, and a button that does nothing is worse than no button — this stage has no
 * token anywhere. The price panel states what a session costs and stops there. D103 §7.
 *
 * <p><b>The headline price is `fromPaidPriceMinor`, exactly as on the card</b> — one quantity, one
 * rendering, three screens. The per-service list beside it shows `priceMinor` per service, which is
 * where "Free" legitimately appears for a service that costs nothing; the listing's headline is
 * never ₵0. D100, D103 §3.
 *
 * <p><b>The availability strip is empty on the quality estate today and that is not a defect
 * here.</b> Measured 2026-09-24: the seed's slots are anchored at 2026-08-10 (`$meta.demoToday`,
 * `anchor-dates=true` on quality), so the endpoint's default ten-day window — from the marketplace's
 * today — is entirely after them and answers `[]` for every professional. The panel says so rather
 * than rendering nothing. Backlog NEW-84.
 *
 * <p><b>`starDistribution` is ONE-STAR FIRST.</b> `[10, 0, 0, 2, 5]` on p1 is ten 1★ and five 5★ —
 * which averages 2.53 against the 2.5 the view reports, and that agreement is how the order was
 * established rather than assumed. The bars are drawn 5★ down, so the array is reversed for display.
 */
@Component({
  selector: 'abm-professional',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './professional.html',
  styleUrl: './professional.scss',
  imports: [RouterLink, TranslateDirective, TranslatePipe, LoadState, Stars],
})
export default class Professional implements OnInit {
  readonly detail = loadableSignal<ProfessionalDetail>();
  readonly availability = loadableSignal<AvailabilityDay[]>();
  readonly reviews = loadableSignal<Page<ReviewView>>();

  readonly ref = signal('');
  readonly reviewPage = signal(0);

  readonly card = computed(() => this.detail().value?.card ?? null);
  readonly verified = computed(() => this.card()?.verification === 'VERIFIED');

  readonly fromPrice = computed(() => money(this.card()?.fromPaidPriceMinor, this.card()?.currency));

  readonly priceState = computed<'paid' | 'free' | 'enquire'>(() => {
    const card = this.card();
    if (!card) return 'enquire';
    if (card.fromPaidPriceMinor !== null) return 'paid';
    return card.hasFreeService ? 'free' : 'enquire';
  });

  /** `[{stars: 5, count}, …, {stars: 1, count}]` — the API's array is 1★ first; bars read downwards. */
  readonly distribution = computed(() => {
    const raw = this.detail().value?.starDistribution ?? [];
    return [5, 4, 3, 2, 1].map(stars => ({ stars, count: raw[stars - 1] ?? 0 }));
  });

  readonly reviewTotal = computed(() => this.reviews().value?.totalElements ?? 0);

  readonly distributionTotal = computed(() => this.distribution().reduce((sum, bucket) => sum + bucket.count, 0));

  readonly reviewPages = computed(() => Array.from({ length: this.reviews().value?.totalPages ?? 0 }, (_unused, index) => index));

  readonly availabilityState = computed<'loading' | 'failed' | 'empty' | 'ready'>(() => {
    const state = this.availability();
    if (state.status !== 'ready') return state.status;
    // `[]` and "ten days of no slots" are the same answer from this endpoint, and the estate's
    // today answers the first. Either way the sentence is "nothing published in this window".
    const days = state.value ?? [];
    return days.some(day => day.slots.length > 0) ? 'ready' : 'empty';
  });

  readonly reviewState = computed<'loading' | 'failed' | 'empty' | 'ready'>(() => {
    const state = this.reviews();
    if (state.status !== 'ready') return state.status;
    return state.value && state.value.content.length > 0 ? 'ready' : 'empty';
  });

  readonly gradient = computed(() => {
    const card = this.card();
    return card ? `linear-gradient(150deg, ${card.avatarGradientFrom}, ${card.avatarGradientTo})` : '';
  });

  money = money;

  private readonly marketplace = inject(MarketplaceService);
  private readonly route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      this.ref.set(paramValue(params, 'ref') ?? '');
      this.reviewPage.set(0);
      this.load();
    });
  }

  load(): void {
    const ref = this.ref();
    drive(this.detail, this.marketplace.professional(ref));
    drive(this.availability, this.marketplace.availability(ref));
    this.loadReviews(0);
  }

  loadReviews(page: number): void {
    this.reviewPage.set(page);
    drive(this.reviews, this.marketplace.reviews(this.ref(), page, REVIEWS_PER_PAGE));
  }

  /** Percentage width of a distribution bar, guarding the 0/0 an unreviewed professional gives. */
  barWidth(count: number): string {
    const total = this.distributionTotal();
    return total === 0 ? '0%' : `${(count / total) * 100}%`;
  }

  /**
   * `""` from the seed and `null` from a review written through the API both mean "no reply", and a
   * template testing only for `null` renders an empty reply block on all 63 seeded reviews.
   */
  hasReply(review: ReviewView): boolean {
    return (review.professionalReply ?? '').trim().length > 0;
  }
}
