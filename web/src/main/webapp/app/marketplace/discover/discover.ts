import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { TranslatePipe } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';

import LoadState from '../load-state/load-state';
import ProfessionalCardComponent from '../professional-card/professional-card';
import { CategoryView, Facets, Page, ProfessionalCard } from '../marketplace.model';
import { Loadable, drive, loadableSignal } from '../load-state';
import { MarketplaceService } from '../marketplace.service';

/**
 * Discover — the marketplace's landing page, and the first screen of this client anybody outside
 * the project can look at.
 *
 * <p><b>Every figure on it comes from the estate.</b> That is the point rather than a nicety: the
 * prototype's "Sessions brokered" tile read a plausible 269 for as long as it existed while the
 * estate seeds 256, because it summed two in-memory arrays. A wrong number that looks right is this
 * project's most expensive defect class, so the three hero figures below are three reads and the
 * fourth tile is <b>omitted</b> — see `sessionsBrokered` in the prototype's live block, which
 * reaches the same conclusion by the same argument: nothing this estate publishes counts bookings
 * for an anonymous reader, and adding such a count is a disclosure decision about estate-wide
 * volume, not a client fix.
 *
 * <p><b>"Available soonest" is omitted too, and that is a decision with a loser — D103 §5.</b> The
 * prototype ranks by `nextAvailable(p.id)`; `ProfessionalCard` carries no availability and no server
 * sort reads one, so the client would need one `/availability` request per card on an
 * unauthenticated public read. Measured on the quality estate on 2026-09-24, every one of those
 * would answer `[]` — the seed's slots are anchored at 2026-08-10 and today's ten-day window is
 * entirely past it — so the section would render empty for every visitor while costing eighteen
 * requests. Backlog NEW-82 is where the server-side answer lives.
 *
 * <p><b>FIVE reads, all `permitAll`, no token anywhere</b> — enumerated because this javadoc named a
 * request the screen does not make until 2026-09-24, and so did D103 §1's table:
 *
 * <ul>
 *   <li>`categories` — the four tiles;</li>
 *   <li>`professionals?sort=recommended&size=4` — the featured strip;</li>
 *   <li>`professionals/facets` <b>unfiltered</b> — the city and format menus, <b>and the
 *       "Professionals" tile, which is `facets().total`</b>;</li>
 *   <li>`reviews/count` — the "Reviews" tile;</li>
 *   <li>`professionals?verifiedOnly=true&size=1` — the "Verified" tile, for `totalElements` alone.</li>
 * </ul>
 *
 * <p><b>`professionals/count` is NOT among them</b>, which is what the old sentence got wrong. The
 * number it returns is the same — measured, both answer `18` — so nothing was on screen incorrectly;
 * the prose described a request that is never sent. The tile reads `facets().total` because this
 * screen needs the facets anyway for its two menus, so a second request for a number already in that
 * response would be a request for nothing.
 *
 * <p>⚠ **`MarketplaceService.professionalCount()` therefore has no caller**, and that is deliberate
 * rather than an oversight: `/api/professionals/count` and `/api/reviews/count` are the pair
 * `deploy-dev.sh`'s `verify_seed` and `quality/startup.sh --verify` compare against the seed file,
 * so they exist for the scripts. The service covers all eight of catalog's public reads because it
 * is the one place this client expresses that API; `reviewCount()` happens to be the one of the pair
 * a screen needs, because there is no facet carrying it.
 */
@Component({
  selector: 'abm-discover',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './discover.html',
  styleUrl: './discover.scss',
  imports: [ReactiveFormsModule, RouterLink, TranslateDirective, TranslatePipe, LoadState, ProfessionalCardComponent],
})
export default class Discover implements OnInit {
  readonly categories = loadableSignal<CategoryView[]>();
  readonly featured = loadableSignal<Page<ProfessionalCard>>();
  /** The unfiltered facets, which is where the city and format menus and the totals come from. */
  readonly overview = loadableSignal<Facets>();
  readonly reviewTotal = signal<number | null>(null);
  readonly verifiedTotal = signal<number | null>(null);

  readonly search = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    city: new FormControl('', { nonNullable: true }),
    mode: new FormControl('', { nonNullable: true }),
  });

  readonly total = computed(() => this.overview().value?.total ?? null);

  private readonly marketplace = inject(MarketplaceService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    drive(this.categories, this.marketplace.categories());
    drive(this.featured, this.marketplace.browse({ sort: 'recommended', size: 4 }));
    drive(this.overview, this.marketplace.facets({}));

    // The two bare counts are not `Loadable` because nothing on the screen depends on telling their
    // failure from their absence: a hero tile with no number is simply not rendered, and the
    // headline state is already carried by `overview`.
    //
    // BOTH CARRY AN `error` ARM, and it is not decoration. A subscription with only `next` rethrows
    // on the error path — RxJS reports it as an unhandled error, which in a browser is an
    // uncaught exception on a page that is otherwise fine. Measured: without these two arms, the
    // failing-estate case in this component's spec produced three "Vitest caught unhandled errors"
    // reports while every assertion stayed green.
    this.reviewTotal.set(null);
    this.marketplace.reviewCount().subscribe({
      next: value => this.reviewTotal.set(value),
      error: () => this.reviewTotal.set(null),
    });
    this.verifiedTotal.set(null);
    this.marketplace.browse({ verifiedOnly: true, size: 1 }).subscribe({
      next: page => this.verifiedTotal.set(page.totalElements),
      error: () => this.verifiedTotal.set(null),
    });
  }

  /** The hero search hands Browse its query in the URL, so a search is a link somebody can share. */
  submitSearch(): void {
    const { q, city, mode } = this.search.getRawValue();
    const queryParams: Record<string, string> = {};
    if (q.trim()) queryParams['q'] = q.trim();
    if (city) queryParams['city'] = city;
    if (mode) queryParams['mode'] = mode;
    void this.router.navigate(['/browse'], { queryParams });
  }

  categoryState = (state: Loadable<CategoryView[]>): 'loading' | 'failed' | 'empty' | 'ready' =>
    state.status === 'ready' ? (state.value?.length ? 'ready' : 'empty') : state.status;

  featuredState = (state: Loadable<Page<ProfessionalCard>>): 'loading' | 'failed' | 'empty' | 'ready' =>
    state.status === 'ready' ? (state.value?.content.length ? 'ready' : 'empty') : state.status;
}
