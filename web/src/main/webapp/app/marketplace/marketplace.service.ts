import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { Observable } from 'rxjs';

import { ApplicationConfigService } from 'app/core/config/application-config.service';
import { MICROSERVICE } from 'app/config/microservices';

import {
  AvailabilityDay,
  BrowseQuery,
  CategoryView,
  Facets,
  Page,
  ProfessionalCard,
  ProfessionalDetail,
  ReviewView,
} from './marketplace.model';

/**
 * The one HTTP client for catalog's public reads — **all eight `@GetMapping`s on
 * `MarketplaceResource`**, which is every entry in its
 * `MarketplacePublicSecurityConfiguration.PUBLIC_GET_PATHS`.
 *
 * <p>This said *"the seven endpoints"* until 2026-09-24 and the review brief said seven too. There
 * are **eight** — `categories`, `professionals`, `professionals/facets`, `professionals/count`,
 * `reviews/count`, `professionals/{ref}`, `professionals/{ref}/availability`,
 * `professionals/{ref}/reviews` — and all eight answer an anonymous caller 200. `professionalCount()`
 * is the one with no caller in this client; `discover.ts`'s javadoc says why.
 *
 * <p><b>Every URL goes through `getEndpointFor(api, microservice)` and the service name comes from
 * `MICROSERVICE`</b>, which is the house pattern and, here, a security property rather than a
 * style: the gateway routes `/services/&lt;service&gt;/api/**` and nothing wider, and catalog's
 * `/internal/**` answers an unauthenticated caller — so the narrowness of those predicates is the
 * only thing keeping it off the internet (D28). `endpoint-construction.spec.ts` refuses a literal.
 *
 * <p><b>No token is sent and none is required.</b> All seven are `permitAll` in catalog's
 * `MarketplacePublicSecurityConfiguration`; the JWT interceptor attaches a token when one happens to
 * be stored and the responses do not change. That is what makes these screens the first thing
 * anybody outside this project can look at.
 *
 * <p><b>Undefined parameters are omitted rather than sent empty.</b> `?city=` is not the same
 * request as no `city` at all: the server treats a blank as absent today (`isBlank` guards in
 * `BrowseFilter.matches`), but relying on that puts this client's correctness inside somebody
 * else's null-handling.
 */
@Injectable({ providedIn: 'root' })
export class MarketplaceService {
  private readonly http = inject(HttpClient);
  private readonly applicationConfigService = inject(ApplicationConfigService);

  private static paramsFor(query: BrowseQuery): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') continue;
      if (key === 'verifiedOnly' && value === false) continue;
      params = params.set(key, String(value));
    }
    return params;
  }

  categories(): Observable<CategoryView[]> {
    return this.http.get<CategoryView[]>(this.catalog('api/categories'));
  }

  browse(query: BrowseQuery): Observable<Page<ProfessionalCard>> {
    return this.http.get<Page<ProfessionalCard>>(this.catalog('api/professionals'), { params: MarketplaceService.paramsFor(query) });
  }

  /**
   * The live counts beside each filter, computed against the OTHER active filters.
   *
   * <p>`sort`, `page` and `size` are not parameters of this endpoint and are dropped rather than
   * sent and ignored — a request carrying a parameter the server does not read is a claim about the
   * API that nothing checks.
   */
  facets(query: BrowseQuery): Observable<Facets> {
    const { sort, page, size, ...facetable } = query;
    return this.http.get<Facets>(this.catalog('api/professionals/facets'), { params: MarketplaceService.paramsFor(facetable) });
  }

  /**
   * **No caller in this client, deliberately.** Discover's "Professionals" tile reads
   * `facets().total` instead, because that screen already fetches the facets for its city and format
   * menus and the two numbers agree (measured: both `18`). This pair of count endpoints exists for
   * `deploy-dev.sh`'s `verify_seed` and `quality/startup.sh --verify`, which compare them against the
   * seed file; the method is here because this service is the one place the public API is expressed.
   */
  professionalCount(): Observable<number> {
    return this.http.get<number>(this.catalog('api/professionals/count'));
  }

  reviewCount(): Observable<number> {
    return this.http.get<number>(this.catalog('api/reviews/count'));
  }

  professional(ref: string): Observable<ProfessionalDetail> {
    return this.http.get<ProfessionalDetail>(this.catalog(`api/professionals/${encodeURIComponent(ref)}`));
  }

  availability(ref: string): Observable<AvailabilityDay[]> {
    return this.http.get<AvailabilityDay[]>(this.catalog(`api/professionals/${encodeURIComponent(ref)}/availability`));
  }

  reviews(ref: string, page: number, size: number): Observable<Page<ReviewView>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ReviewView>>(this.catalog(`api/professionals/${encodeURIComponent(ref)}/reviews`), { params });
  }

  /**
   * `ref` is interpolated into the path above, so it is percent-encoded at every call site. Refs are
   * `p1`-shaped today and a route parameter is whatever the address bar carries; an unencoded `../`
   * would address a different endpoint through the same route predicate.
   */
  private catalog(api: string): string {
    return this.applicationConfigService.getEndpointFor(api, MICROSERVICE.catalog);
  }
}
