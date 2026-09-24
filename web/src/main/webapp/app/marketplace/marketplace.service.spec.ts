import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { MICROSERVICE } from 'app/config/microservices';

import { MarketplaceService } from './marketplace.service';

describe('MarketplaceService', () => {
  let service: MarketplaceService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MarketplaceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('routes every read through the catalog microservice prefix', () => {
    // The gateway routes `/services/<service>/api/**` and nothing wider, and CLAUDE.md calls that
    // narrowness a security control — catalog's `/internal/**` answers an unauthenticated caller and
    // the only thing keeping it off the internet is that no route matches it. So the prefix is not
    // decoration; a client that gets it wrong is a client whose reads silently 404 at the edge.
    service.categories().subscribe();
    const request = httpMock.expectOne(`services/${MICROSERVICE.catalog}/api/categories`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('omits an absent filter rather than sending it empty', () => {
    // `?city=` is not the same request as no `city`. The server happens to treat a blank as absent
    // today; relying on that would put this client's correctness inside somebody else's null check.
    service.browse({ category: 'CARE', city: undefined, q: '', size: 6 }).subscribe();
    const request = httpMock.expectOne(req => req.url.endsWith('/api/professionals'));
    expect(request.request.params.get('category')).toBe('CARE');
    expect(request.request.params.has('city')).toBe(false);
    expect(request.request.params.has('q')).toBe(false);
    expect(request.request.params.get('size')).toBe('6');
    request.flush({ content: [], page: 0, size: 6, totalElements: 0, totalPages: 0 });
  });

  it('does not send verifiedOnly when it is false', () => {
    // `verifiedOnly=false` is the server's own default, and a URL carrying it makes an unfiltered
    // Browse look filtered — which is what the "clear all filters" control keys on.
    service.browse({ verifiedOnly: false }).subscribe();
    const request = httpMock.expectOne(req => req.url.endsWith('/api/professionals'));
    expect(request.request.params.has('verifiedOnly')).toBe(false);
    request.flush({ content: [], page: 0, size: 6, totalElements: 0, totalPages: 0 });
  });

  it('drops sort, page and size from a facets request', () => {
    // `MarketplaceResource.facets` declares none of the three. A request carrying a parameter the
    // server does not read is a claim about the API that nothing checks.
    service.facets({ category: 'CARE', sort: 'rating', page: 2, size: 6 }).subscribe();
    const request = httpMock.expectOne(req => req.url.endsWith('/api/professionals/facets'));
    expect(request.request.params.get('category')).toBe('CARE');
    expect(request.request.params.has('sort')).toBe(false);
    expect(request.request.params.has('page')).toBe(false);
    expect(request.request.params.has('size')).toBe(false);
    request.flush({ categories: [], specialities: [], cities: [], deliveryModes: [], total: 0, minPriceMinor: null, maxPriceMinor: null });
  });

  it('percent-encodes the reference it puts in a path', () => {
    // A route parameter is whatever the address bar carries. Refs are `p1`-shaped today; an
    // unencoded `../` would address a different endpoint through the same route predicate.
    service.professional('p 1/../internal').subscribe({ error: () => undefined });
    const request = httpMock.expectOne(req => req.url.includes('/api/professionals/'));
    expect(request.request.url).toContain('p%201%2F..%2Finternal');
    expect(request.request.url).not.toContain('/../');
    request.flush(null, { status: 404, statusText: 'Not Found' });
  });

  it('asks for the reviews page it was given', () => {
    service.reviews('p1', 2, 4).subscribe();
    const request = httpMock.expectOne(req => req.url.endsWith('/api/professionals/p1/reviews'));
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('4');
    request.flush({ content: [], page: 2, size: 4, totalElements: 0, totalPages: 0 });
  });

  it('sends no Authorization header of its own', () => {
    // Every one of these endpoints is `permitAll`. Stage B is the part of the client somebody with
    // no account can look at, and the service must not be the thing that changes that.
    service.reviewCount().subscribe();
    const request = httpMock.expectOne(req => req.url.endsWith('/api/reviews/count'));
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(73);
  });
});
