import { ParamMap } from '@angular/router';

/**
 * Reading one route or query parameter, with absent and empty collapsed into `undefined`.
 *
 * <p><b>This exists for two reasons and only one of them is about routing.</b>
 *
 * <p>The first is ordinary: `params.get(key) ?? undefined` appeared eleven times in `Browse` alone,
 * and `''` and `null` mean the same thing to every filter on this screen — an empty search box is
 * not a search, and `?city=` is not a city.
 *
 * <p><b>The second is that `endpoint-construction.spec.ts` matches on the METHOD NAME, so a literal
 * `params.get('category')` is an offender to it.</b> That guard refuses any call to something named
 * `get`/`post`/… whose first argument is a string literal, because its subject is a hand-written URL
 * handed to `HttpClient` and it cannot tell one receiver from another. Its javadoc says so and calls
 * the over-match the fail-closed direction — it names `Map.get('key')` explicitly.
 *
 * <p><b>Stage B is the first code to trip it, and the guard was NOT narrowed.</b> Loosening a ban the
 * moment it first fires is this repository's standing failure mode, stated in D97 §5 about a
 * different check: <i>the cheap way to keep it green is to drop a real ERROR to WARN at a site that
 * knows it is an error, and that is this defect inverted.</i> The reach that would have been given
 * up is real — `ParamMap` is not the only thing in Angular with a `get`, and the URL literal the
 * guard exists for could appear beside one. So the code moved instead, and it reads better for it.
 *
 * <p>The next screen family will meet this again: `ActivatedRoute.paramMap.get(...)` is the
 * idiomatic call and Stages C, D and E are full of it. Reach for this rather than for the guard.
 */
export const paramValue = (params: ParamMap, key: string): string | undefined => {
  const raw = params.get(key);
  return raw === null || raw.trim() === '' ? undefined : raw;
};

/** `?verifiedOnly=true` and nothing else. Anything unrecognised is the default, never a crash. */
export const paramFlag = (params: ParamMap, key: string): boolean => paramValue(params, key) === 'true';

/** A finite number, or `undefined`. `?page=banana` is not page NaN. */
export const paramNumber = (params: ParamMap, key: string): number | undefined => {
  const raw = paramValue(params, key);
  if (raw === undefined) return undefined;
  const value = Number(raw);
  return Number.isFinite(value) ? value : undefined;
};
