import { beforeEach, describe, expect, it, vitest } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { provideTranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { FACETS, P12_CARD, P1_CARD, loadEnglish, pageOf } from '../marketplace.fixtures';
import { BrowseQuery, Facets } from '../marketplace.model';
import { MarketplaceService } from '../marketplace.service';

import Browse from './browse';

describe('Browse', () => {
  let fixture: ComponentFixture<Browse>;
  let queryParams: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let marketplace: {
    browse: ReturnType<typeof vitest.fn>;
    facets: ReturnType<typeof vitest.fn>;
  };

  const create = async (): Promise<void> => {
    await TestBed.configureTestingModule({
      imports: [Browse],
      providers: [
        provideRouter([]),
        provideTranslateService(),
        { provide: MarketplaceService, useValue: marketplace },
        { provide: ActivatedRoute, useValue: { queryParamMap: queryParams.asObservable() } },
      ],
    }).compileComponents();
    // The real bundle, as in `discover.spec.ts`: the empty-state assertions below read the words a
    // visitor is shown, and with no bundle loaded `abm-load-state` renders the translation KEY, so
    // "the screen says the catalogue is empty" would be true of a screen saying
    // `marketplace.browse.emptyCatalogue.title`. See `terms-are-not-quoted.spec.ts`.
    loadEnglish();
    fixture = TestBed.createComponent(Browse);
    fixture.detectChanges();
  };

  /** The query the component last asked the server for. */
  const lastQuery = (): BrowseQuery => marketplace.browse.mock.calls.at(-1)?.[0] as BrowseQuery;

  beforeEach(() => {
    queryParams = new BehaviorSubject(convertToParamMap({}));
    marketplace = {
      browse: vitest.fn(() => of(pageOf([P1_CARD, P12_CARD]))),
      facets: vitest.fn(() => of(FACETS)),
    };
  });

  it('reads its whole state out of the URL', async () => {
    // A filtered catalogue must be a link somebody can send, and the back button must do what it
    // looks like it does.
    queryParams.next(
      convertToParamMap({ category: 'CARE', city: 'Kumasi', maxPriceMinor: '20000', minRating: '4.5', verifiedOnly: 'true', page: '2' }),
    );
    await create();
    expect(lastQuery()).toMatchObject({
      category: 'CARE',
      city: 'Kumasi',
      maxPriceMinor: 20000,
      minRating: 4.5,
      verifiedOnly: true,
      page: 2,
    });
  });

  it('draws the price slider from the same quantity the card shows', async () => {
    // D100 §4's fourth site, one layer up. `Facets.minPriceMinor`/`maxPriceMinor` read
    // `fromPaidPriceMinor`, which is the card's headline and what `maxPriceMinor` filters on —
    // measured 9000–42000 on the quality estate. Bind the slider to `fromPriceMinor` and its floor
    // is ₵0, a bracket the filter then matches nobody in.
    await create();
    expect(fixture.componentInstance.priceFloor()).toBe(9000);
    expect(fixture.componentInstance.priceCeiling()).toBe(42000);
  });

  it('reads the slider scale ONCE, unfiltered, so it cannot move under the thumb', async () => {
    // `facets(query)` tallies over the FILTERED set, so the range narrows as you filter. The first
    // call is the unfiltered one and nothing later replaces `options`.
    await create();
    expect(marketplace.facets).toHaveBeenNthCalledWith(1, {});

    marketplace.facets.mockReturnValue(of({ ...FACETS, minPriceMinor: 28000, maxPriceMinor: 28000 } satisfies Facets));
    queryParams.next(convertToParamMap({ city: 'Kumasi' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.priceFloor()).toBe(9000);
    expect(fixture.componentInstance.priceCeiling()).toBe(42000);
  });

  it('sends no budget at the top of the scale', async () => {
    // ₵420 is the highest FLOOR in the catalogue, not the highest price, so "up to ₵420" already
    // means everybody — and sending it would exclude any listing whose floor is above the range the
    // facets last reported.
    await create();
    const router = TestBed.inject(Router);
    const navigate = vitest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.setPrice('42000');
    expect(navigate.mock.calls[0][1]?.queryParams).not.toHaveProperty('maxPriceMinor');

    fixture.componentInstance.setPrice('20000');
    expect(navigate.mock.calls[1][1]?.queryParams).toMatchObject({ maxPriceMinor: '20000' });
  });

  it('deletes minRating for "any rating" rather than sending zero', async () => {
    // `minRating=0` would be a filter the server applies — and it EXCLUDES unrated professionals
    // rather than reading them as 0.0, so "any rating" would silently drop every unrated listing.
    await create();
    const router = TestBed.inject(Router);
    const navigate = vitest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.setRating(null);
    expect(navigate.mock.calls[0][1]?.queryParams).not.toHaveProperty('minRating');
  });

  it('returns to the first page whenever a filter changes', async () => {
    // Page 4 of 2 results is nothing at all, and the pager would render one button.
    queryParams.next(convertToParamMap({ page: '3' }));
    await create();
    const router = TestBed.inject(Router);
    const navigate = vitest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.set('city', 'Accra');
    expect(navigate.mock.calls[0][1]?.queryParams).not.toHaveProperty('page');
  });

  it('keeps every filter option visible once one of them is chosen', async () => {
    // `facets` tallies over the FULLY filtered set, so with `city=Accra` chosen the filtered facets
    // hold Accra alone. Options come from the unfiltered read; without that, choosing a city removes
    // every other city from the sidebar and the filter cannot be changed back.
    queryParams.next(convertToParamMap({ city: 'Accra' }));
    marketplace.facets = vitest.fn((query: BrowseQuery) =>
      of(query.city ? { ...FACETS, cities: [{ value: 'Accra', label: 'Accra', count: 9 }] } : FACETS),
    );
    await create();
    expect(fixture.componentInstance.options()?.cities).toHaveLength(2);
  });

  it('suppresses a count inside the group that carries the selection', async () => {
    // With `city=Accra` the filtered count beside "Kumasi" is 0 and means "0 within Accra", not "no
    // professionals in Kumasi". A wrong count is worse than none.
    queryParams.next(convertToParamMap({ city: 'Accra' }));
    await create();
    expect(fixture.componentInstance.countFor('cities', 'Kumasi')).toBeNull();
    expect(fixture.componentInstance.countFor('categories', 'CARE')).toBe(4);
  });

  it('offers only sort orders the server implements', async () => {
    // The prototype's seventh, "Available soonest", has no server equivalent and no field on the
    // card to compute it from — D103 §5, backlog NEW-82. A menu entry the server silently ignores
    // reorders nothing and reads as a broken control.
    await create();
    expect([...fixture.componentInstance.sorts]).toEqual([
      'recommended',
      'rating',
      'reviews',
      'price-asc',
      'price-desc',
      'experience',
      'response',
    ]);
    expect(fixture.componentInstance.sorts).not.toContain('soon');
  });

  it('SHOWS the sort it applied, for a sort that arrived in the URL', async () => {
    // ⚠ THE CONTROL AND THE RESULT MUST AGREE, AND THE FIRST VERSION OF THIS SCREEN BROKE THAT ON
    // EVERY SHARED LINK. `[value]` on a `<select>` whose options come from an `@for` is written
    // BEFORE the options exist, so the DOM keeps `''`, the browser default-selects option 0, and
    // nothing rewrites it because the bound value never changes again. Measured through CDP against
    // the live gateway: `?sort=price-asc` returned p3/p9/p4 — correct — while the menu read "Most
    // relevant", and the same for `rating`, `reviews` and `experience`. First render only, which is
    // every shared link, bookmark, reload and back-navigation.
    //
    // THIS COMPONENT'S OWN CODE PREDICTED IT. `asSort` drops an unknown sort rather than forwarding
    // it because that "would leave the menu showing nothing selected while the list was ordered by
    // something — the shape that reads as a broken control". That shape was live for all seven
    // KNOWN sorts.
    //
    // So the assertion is over the RENDERED selection and not over a signal: `query().sort` was
    // correct the whole time, which is why nothing in this file could see it.
    for (const sort of ['price-asc', 'rating', 'reviews', 'experience']) {
      queryParams.next(convertToParamMap({ sort }));
      await create();
      const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('[data-cy="browseSort"]');
      expect(select, 'the sort menu is not rendered').not.toBeNull();
      expect(lastQuery().sort, `${sort} did not reach the server`).toBe(sort);
      expect(select!.value, `the menu does not show the ${sort} order the list is in`).toBe(sort);
      expect(select!.selectedOptions[0].value, `no option is selected for ${sort}`).toBe(sort);
      TestBed.resetTestingModule();
    }
  });

  it('shows "recommended" when the URL names no sort', async () => {
    // The default arm of the same property: `query().sort` is `undefined` here, so a binding that
    // renders nothing selected and a binding that renders the right thing look identical in the
    // signal.
    //
    // ⚠ THIS CASE CANNOT FAIL FOR THE REASON IT LOOKS LIKE IT TESTS, and it said the opposite until
    // D103 §14. It read "it is here so the fix is not 'always select option 0'" — measured, dropping
    // the `?? 'recommended'` fallback so that NO option is marked selected leaves it green, because
    // `select.value` is defined as the value at `selectedIndex` and the browser default-selects
    // option 0. D103 §13(a) has it right and that comment had it backwards. What this case does cover
    // is that a no-sort URL still renders a MENU WITH A SELECTION — a binding that blanked the
    // control, or reordered the options so that option 0 is not `recommended`, is red here. The
    // defect above is caught by the cases that name a non-default sort, and only by those.
    await create();
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('[data-cy="browseSort"]');
    expect(select!.value).toBe('recommended');
  });

  it('still shows the sort a reader chose in the app', async () => {
    // THE CONTROL FOR THE FIX. Choosing a sort inside the app always worked — the `change` handler
    // navigates and `query()` comes back changed — so a repair that only fixed first render would
    // leave this green and a repair that broke the interactive path would be red here alone.
    await create();
    const router = TestBed.inject(Router);
    vitest.spyOn(router, 'navigate').mockImplementation((_commands, extras) => {
      queryParams.next(convertToParamMap(extras?.queryParams ?? {}));
      return Promise.resolve(true);
    });
    fixture.componentInstance.set('sort', 'price-desc');
    fixture.detectChanges();
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('[data-cy="browseSort"]');
    expect(select!.value).toBe('price-desc');
  });

  it('ignores a sort the server does not implement', async () => {
    // Forwarding it would work — the server falls back to `recommended` — and would leave the menu
    // showing nothing selected while the list was ordered by something.
    queryParams.next(convertToParamMap({ sort: 'soon' }));
    await create();
    expect(lastQuery().sort).toBeUndefined();
  });

  it('distinguishes no results from an unreachable catalogue', async () => {
    marketplace.browse = vitest.fn(() => of(pageOf([])));
    await create();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-cy="stateEmpty"]')).not.toBeNull();

    marketplace.browse = vitest.fn(() => throwError(() => new Error('down')));
    TestBed.resetTestingModule();
    await create();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-cy="stateFailed"]')).not.toBeNull();
  });

  it('tells a filtered dead end from an empty catalogue, in the words a visitor reads', async () => {
    // BOTH ARMS OF THE EMPTY STATE, rendered. The `hasFilters()` assertions below test the COMPUTED
    // and nothing in this file rendered either arm until D103 §14 — which is the same gap review
    // round 1 found one component along, reintroduced in the same commit that fixed it. The arm that
    // was untested is the one nothing can reach on quality: production's first day, catalogue empty,
    // no filter set, where the other arm's words tell a visitor to widen a price range they never
    // touched.
    marketplace.browse = vitest.fn(() => of(pageOf([])));
    await create();
    let text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text, 'the bundle did not load — the assertions here would read keys').not.toContain('marketplace.');
    expect(text).toContain('No professionals are listed yet');
    expect(text, 'an unfiltered visitor was told to widen a filter').not.toContain('Nothing matches those filters');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-cy="clearFiltersEmpty"]'), 'there is nothing to clear').toBeNull();

    TestBed.resetTestingModule();
    queryParams.next(convertToParamMap({ city: 'Accra' }));
    await create();
    text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Nothing matches those filters');
    expect(text, 'a filtered visitor was told the marketplace is empty').not.toContain('No professionals are listed yet');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-cy="clearFiltersEmpty"]')).not.toBeNull();
  });

  it('offers "clear all" only when something is filtering', async () => {
    await create();
    expect(fixture.componentInstance.hasFilters()).toBe(false);

    queryParams.next(convertToParamMap({ sort: 'rating' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.hasFilters(), 'a sorted catalogue is still the whole catalogue').toBe(false);

    queryParams.next(convertToParamMap({ city: 'Accra' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.hasFilters()).toBe(true);
  });
});
