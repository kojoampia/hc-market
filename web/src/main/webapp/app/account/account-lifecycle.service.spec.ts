import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { MICROSERVICE } from 'app/config/microservices';

import { AccountLifecycleService } from './account-lifecycle.service';

/**
 * **The two API paths behind the two mail addresses, pinned as literals.**
 *
 * <p>This is the one place in the client where both halves of NEW-60's asymmetry are written down,
 * so it is the one place a test can hold them: the mail composes `/account/activate` and
 * `/account/reset/finish`, while the API is `/api/activate` and
 * `/api/account/reset-password/finish`. Only the first pair rhymes. A reader applying a prefix rule
 * to the second gets `api/account/reset/finish`, which this estate does not map — every Spring
 * service here 404s a path it does not map, and nothing else in the client would say so.
 */
describe('AccountLifecycleService', () => {
  let service: AccountLifecycleService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountLifecycleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('activates through GET api/activate with the key as a query parameter', () => {
    service.activate('a-real-looking-key').subscribe();
    const request = httpMock.expectOne(req => req.url === 'api/activate');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('key')).toBe('a-real-looking-key');
    request.flush(null);
  });

  it('finishes a reset through POST api/account/reset-password/finish', () => {
    // NOT `api/account/reset/finish`. The frontend route and the API path differ in spelling and in
    // segment count, which is the trap NEW-60 names.
    service.finishPasswordReset('a-key', 'a-password').subscribe();
    const request = httpMock.expectOne('api/account/reset-password/finish');
    expect(request.request.method).toBe('POST');
    // The field names are `KeyAndPasswordVM`'s, read off the gateway rather than guessed. A wrong
    // name binds to null, and `isPasswordLengthInvalid(null)` is a 400 that reads as a rejected
    // password — the quietest possible way to get this wrong.
    expect(request.request.body).toEqual({ key: 'a-key', newPassword: 'a-password' });
    request.flush(null);
  });

  it('addresses the GATEWAY and not a microservice', () => {
    // THE CONTROL, and it is a security property rather than tidiness. These two endpoints are the
    // gateway's own; `MICROSERVICE` deliberately has no entry for the gateway, and
    // `getEndpointFor(api)` with no second argument is what addresses it. A `services/…` prefix here
    // would be routed at the edge to a service that does not have these paths and 404 for every
    // person following a link out of a mail message.
    service.activate('k').subscribe();
    service.finishPasswordReset('k', 'p').subscribe();
    for (const name of Object.values(MICROSERVICE)) {
      expect(
        httpMock.match(req => req.url.includes(name)),
        `an account call was routed through ${name}`,
      ).toEqual([]);
    }
    httpMock.match(() => true).forEach(request => request.flush(null));
  });

  it('sends no Authorization header of its own, because neither endpoint takes one', () => {
    // Both are `permitAll` in the gateway's `SecurityConfiguration`, and they have to be: somebody
    // following an activation link has no account they can sign in to yet. The generated JWT
    // interceptor attaches a token when one happens to be stored, which is why this asserts over
    // what the SERVICE sets rather than over what finally goes on the wire.
    service.activate('k').subscribe();
    const request = httpMock.expectOne(req => req.url === 'api/activate');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null);
  });
});
