/**
 * The four microservices behind the gateway, by the name that appears in a routed URL.
 *
 * <p>These are the second argument to {@link
 * import('app/core/config/application-config.service').ApplicationConfigService.getEndpointFor} and
 * they are the ONLY place those names are written in this application. The gateway routes
 * `/services/&lt;service&gt;/api/**` and nothing wider — `hc-market/CLAUDE.md` calls the narrowness of
 * those predicates a security control, because catalog's `/internal/**` answers an unauthenticated
 * caller and the only thing keeping it off the internet is that no route matches it. So a client
 * that writes `/services/…` by hand is writing a path whose correctness depends on a predicate it
 * cannot see.
 *
 * <p><b>The values are taken from the route predicates themselves</b>, `quality/compose.yml:500-512`
 * and their opposite numbers in `deploy/docker/docker-compose.dev.yml` and
 * `deploy/docker/docker-compose.prod.yml` — not from the JDL `baseName`, which happens to agree
 * today and is a different fact.
 *
 * <p>Nothing in Stage A calls any of them: the scaffold's own screens address the gateway directly
 * (`api/account`, `api/authenticate`, `management/*`). They are declared now so that the first screen
 * that needs one has somewhere to reach for, and so `endpoint-construction.spec.ts` has a set to
 * check literals against. The gateway itself deliberately has no entry — `getEndpointFor(api)` with
 * no second argument is what addresses it.
 */
export const MICROSERVICE = {
  catalog: 'healthconnectcatalog',
  booking: 'healthconnectbooking',
  messaging: 'healthconnectmessaging',
  payout: 'healthconnectpayout',
} as const;

export type Microservice = (typeof MICROSERVICE)[keyof typeof MICROSERVICE];
