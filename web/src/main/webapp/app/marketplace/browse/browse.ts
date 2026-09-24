import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';

import { TranslatePipe } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language';

import LoadState from '../load-state/load-state';
import ProfessionalCardComponent from '../professional-card/professional-card';
import { BROWSE_SORTS, BrowseQuery, Facets, Page, ProfessionalCard } from '../marketplace.model';
import { drive, loadableSignal } from '../load-state';
import { MarketplaceService } from '../marketplace.service';
import { money } from '../money';
import { paramFlag, paramNumber, paramValue } from '../route-params';

/** The prototype's page size, kept so a page of Browse is the same shape it has always been. */
const PAGE_SIZE = 6;

/** The rating steps the prototype offers. `null` is "any rating". */
export const RATING_STEPS: (number | null)[] = [null, 4, 4.5, 4.8];

/**
 * Browse — the filtered catalogue.
 *
 * <p><b>THE STATE IS THE URL.</b> Every filter, the sort and the page are query parameters, so a
 * filtered catalogue is a link somebody can send and the back button does what it looks like it
 * does. Discover's hero search navigates here with `q`, `city` and `mode` rather than pushing state
 * through a service.
 *
 * <p><b>THE PRICE CONTROL BINDS TO THE SAME QUANTITY THE CARD SHOWS — D100, D103 §3.</b> The slider
 * sends `maxPriceMinor`, which the server applies to `fromPaidPriceMinor`, which is the number on
 * the card; and its range comes from `Facets.minPriceMinor`/`maxPriceMinor`, which read that same
 * quantity. Bind it to `fromPriceMinor` instead and the slider's floor is ₵0 — a bracket the filter
 * then matches nobody in, which is the exact defect D100 §4 found and fixed one layer down.
 *
 * <p><b>The range is read ONCE, unfiltered, and does not move.</b> `facets(query)` tallies over the
 * filtered set, so the range narrows as you filter; a slider whose own scale moved under the thumb
 * would be unusable and would make "up to ₵420" mean a different thing on every render.
 *
 * <p><b>At the top of the range no `maxPriceMinor` is sent at all.</b> ₵420 is the highest FLOOR in
 * the catalogue, not the highest price, so "up to ₵420" already means everybody — and sending it
 * would silently exclude any listing whose floor is above the range the facets last reported.
 *
 * <p><b>Every filter is single-valued because the server's is</b> — `category`, `speciality`, `mode`
 * and `city` are one `String` each on `MarketplaceResource`. The prototype renders checkbox groups;
 * doing that here would mean unioning result sets in the client, and the server pages the result, so
 * the client would be filtering one page of six. Radio groups with an explicit "Any" are the honest
 * rendering of the API that exists. D103 §4; a multi-valued filter is a server change.
 *
 * <p><b>A count is suppressed inside the group that carries a selection</b>, and that is a
 * correctness point rather than taste: `facets` tallies over the FULLY filtered set, so with
 * `city=Accra` chosen the count beside "Kumasi" is 0 and means "0 within Accra", not "no
 * professionals in Kumasi". The prototype computes each group's counts ignoring that group's own
 * selection; this API cannot answer that in one request, so the counts are hidden rather than
 * shown wrong. The OPTIONS still all appear, because they come from the unfiltered facets — without
 * that, choosing a city removes every other city from the sidebar and the filter cannot be changed.
 */
@Component({
  selector: 'abm-browse',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './browse.html',
  styleUrl: './browse.scss',
  imports: [RouterLink, TranslateDirective, TranslatePipe, LoadState, ProfessionalCardComponent],
})
export default class Browse implements OnInit {
  readonly sorts = BROWSE_SORTS;
  readonly ratingSteps = RATING_STEPS;

  readonly results = loadableSignal<Page<ProfessionalCard>>();
  /** Counts against the current filters. */
  readonly counts = loadableSignal<Facets>();
  /** The full option lists and the price range. Read once, unfiltered, and never re-read. */
  readonly options = signal<Facets | null>(null);

  readonly query = signal<BrowseQuery>({});

  readonly priceFloor = computed(() => this.options()?.minPriceMinor ?? null);
  readonly priceCeiling = computed(() => this.options()?.maxPriceMinor ?? null);

  /** The thumb's position: the chosen budget, or the ceiling when no budget is chosen. */
  readonly priceValue = computed(() => this.query().maxPriceMinor ?? this.priceCeiling());

  readonly priceLabel = computed(() => {
    const value = this.priceValue();
    if (value === null) return null;
    const formatted = money(value, this.currency());
    return this.query().maxPriceMinor === undefined ? `${formatted}+` : formatted;
  });

  /**
   * The sort the menu must SHOW, which is the sort the list is actually in.
   *
   * <p>Named rather than inlined because the template binds it per option — `[value]` on the
   * `<select>` is written before an `@for` has created any option, so the DOM keeps `''` and the
   * browser default-selects option 0. See the comment at that binding; it was live against the real
   * estate for all seven known sorts and `asSort` below argues against exactly that shape.
   */
  readonly selectedSort = computed(() => this.query().sort ?? 'recommended');

  readonly currency = computed(() => this.results().value?.content[0]?.currency ?? 'GHS');

  readonly total = computed(() => this.results().value?.totalElements ?? 0);
  readonly pageIndex = computed(() => this.results().value?.page ?? 0);
  readonly totalPages = computed(() => this.results().value?.totalPages ?? 0);
  readonly pageNumbers = computed(() => Array.from({ length: this.totalPages() }, (_unused, index) => index));

  readonly resultState = computed<'loading' | 'failed' | 'empty' | 'ready'>(() => {
    const state = this.results();
    if (state.status !== 'ready') return state.status;
    return state.value && state.value.content.length > 0 ? 'ready' : 'empty';
  });

  /**
   * Whether anything is filtering, which decides whether "clear all" is offered. `size` and `page`
   * are not filters and `sort` is not either — a sorted catalogue is still the whole catalogue.
   */
  readonly hasFilters = computed(() => {
    const { sort, page, size, ...filters } = this.query();
    return Object.values(filters).some(value => Browse.isSet(value));
  });

  /** Used by the template for the slider's scale. */
  money = money;

  private readonly marketplace = inject(MarketplaceService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /**
   * An unknown `sort` becomes `undefined` rather than being passed through. The server falls back
   * to `recommended` for anything it does not recognise, so forwarding rubbish would work and would
   * leave the menu showing nothing selected while the list was ordered by something — the shape
   * that reads as a broken control.
   */
  private static asSort(raw: string | undefined): string | undefined {
    return raw !== undefined && (BROWSE_SORTS as readonly string[]).includes(raw) ? raw : undefined;
  }

  /**
   * One rule for "this value means something", used by `hasFilters` and by the URL writer.
   *
   * <p>`false` counts as absent because `verifiedOnly=false` is the default and putting it in the
   * URL would make an unfiltered Browse look filtered; `''` counts as absent because an empty search
   * box is not a search.
   */
  private static isSet(value: unknown): boolean {
    return value !== undefined && value !== null && value !== '' && value !== false;
  }

  ngOnInit(): void {
    // The option lists and the price range: once, unfiltered, so the sidebar can always be changed
    // and the slider's scale cannot move under the thumb.
    // The `error` arm is required rather than tidy: a subscription with only `next` rethrows on the
    // error path and RxJS reports an unhandled error, which in a browser is an uncaught exception on
    // a page whose own failure state is already handled. Leaving `options` null is correct — the
    // slider and the option lists are simply not rendered.
    this.marketplace.facets({}).subscribe({
      next: facets => this.options.set(facets),
      error: () => this.options.set(null),
    });

    this.route.queryParamMap.subscribe(params => {
      const query: BrowseQuery = {
        category: paramValue(params, 'category'),
        speciality: paramValue(params, 'speciality'),
        mode: paramValue(params, 'mode'),
        city: paramValue(params, 'city'),
        maxPriceMinor: paramNumber(params, 'maxPriceMinor'),
        minRating: paramNumber(params, 'minRating'),
        verifiedOnly: paramFlag(params, 'verifiedOnly'),
        q: paramValue(params, 'q'),
        sort: Browse.asSort(paramValue(params, 'sort')),
        page: paramNumber(params, 'page') ?? 0,
        size: PAGE_SIZE,
      };
      this.query.set(query);
      this.load();
    });
  }

  load(): void {
    drive(this.results, this.marketplace.browse(this.query()));
    drive(this.counts, this.marketplace.facets(this.query()));
  }

  /** Writes one filter into the URL. Any filter change resets to page 0 — page 4 of 2 is nothing. */
  set(key: keyof BrowseQuery, value: string | number | boolean | undefined): void {
    const next: Record<string, unknown> = { ...this.query(), [key]: value };
    if (key !== 'page') next['page'] = 0;
    void this.router.navigate([], { relativeTo: this.route, queryParams: this.toParams(next) });
  }

  clear(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  /** The slider: at the ceiling, no budget is expressed, so no parameter is sent. */
  setPrice(raw: string): void {
    const value = Number(raw);
    this.set('maxPriceMinor', Number.isFinite(value) && value < (this.priceCeiling() ?? Infinity) ? value : undefined);
  }

  /** `null` from the "Any rating" step, which must delete the parameter rather than send `0`. */
  setRating(value: number | null): void {
    this.set('minRating', value ?? undefined);
  }

  countFor(group: 'categories' | 'specialities' | 'cities' | 'deliveryModes', value: string): number | null {
    const selected: Record<typeof group, string | undefined> = {
      categories: this.query().category,
      specialities: this.query().speciality,
      cities: this.query().city,
      deliveryModes: this.query().mode,
    };
    // Suppressed inside a group that carries a selection — see the class javadoc. A wrong count is
    // worse than none: it would read as "there is nobody in Kumasi".
    if (selected[group] !== undefined) return null;
    return this.counts().value?.[group].find(facet => facet.value === value)?.count ?? 0;
  }

  /**
   * The query as URL parameters. `size` never travels — it is this screen's constant, not the
   * reader's choice — and `page=0` is dropped so the first page has a clean address.
   */
  private toParams(query: Record<string, unknown>): Params {
    const params: Params = {};
    for (const [key, value] of Object.entries(query)) {
      if (key === 'size') continue;
      if (!Browse.isSet(value)) continue;
      if (key === 'page' && value === 0) continue;
      params[key] = String(value);
    }
    return params;
  }
}
