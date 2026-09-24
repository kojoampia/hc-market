import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';

import { TranslateService } from '@ngx-translate/core';
import { TestBed } from '@angular/core/testing';

import { CategoryView, Facets, Page, ProfessionalCard, ProfessionalDetail, ReviewView } from './marketplace.model';

/**
 * Cards and facets **read off the running quality estate**, not invented.
 *
 * <p>Every literal below was taken from
 * `http://127.0.0.1:15509/services/healthconnectcatalog/api/…` on 2026-09-24 against the stack at
 * `e331f8e`. That matters more than it sounds: this repository's most expensive defect class is a
 * number that looks right — the prototype's "Sessions brokered" tile read a plausible 269 for as
 * long as it existed while the estate seeds 256 — and a fixture somebody typed from memory
 * reproduces whatever they believed rather than whatever the API does.
 *
 * <p><b>p12 is here because it is the D100 case</b>: `fromPriceMinor: 0` with
 * `fromPaidPriceMinor: 28000` and `hasFreeService: true`. Together with p1 (no free service) it is
 * two of the four price states a card can be in; the other two have never existed in either estate
 * and are constructed here deliberately, because they are exactly the ones nothing else can catch.
 */

/**
 * Loads the REAL `i18n/en/*.json` into the TestBed's translate service, deep-merged.
 *
 * <p>The merge agrees with `build-plugins/i18n-esbuild.ts` **for this data and not by construction**
 * (D103 §14): that plugin uses npm `deepmerge`, which CONCATENATES arrays, while the hand-rolled
 * merge below would merge them index-wise. Measured across the fourteen bundles — 389 leaves, every
 * one a string, **zero arrays**, and one shared top-level key (`error`, in `error.json` and
 * `global.json`) which both algorithms deep-merge identically. So the day a bundle grows an array,
 * this claim needs re-measuring or this function needs `deepmerge`.
 *
 * <p><b>Call this in any spec that asserts over rendered TEXT.</b> `provideTranslateService()` alone
 * loads nothing, and `TranslateDirective` sets `innerHTML` from the translation — so with no bundle
 * ngx-translate returns the KEY, every `abmTranslate` element renders
 * `marketplace.price.free` rather than "Free", and **a template's fallback prose is never in the
 * test DOM at all**. That is not a cosmetic difference: it is what made the first version of the
 * commission-rate guard reach only the one site nobody would ever use — measured, the 12% sentence
 * could be pasted into a fallback or into the bundle and 267 tests stayed green. See
 * `terms-are-not-quoted.spec.ts`.
 *
 * <p>⚠ **This module is TEST-ONLY and uses `node:fs`.** Nothing under `app/` that the application
 * reaches may import it, or the production build breaks — it is reachable from specs alone, and
 * `terms-are-not-quoted.spec.ts` is where a rule about that would go if it ever needs one.
 */
export const loadEnglish = (): void => {
  const dir = 'src/main/webapp/i18n/en';
  const merge = (into: Record<string, unknown>, from: Record<string, unknown>): Record<string, unknown> => {
    for (const [key, value] of Object.entries(from)) {
      const existing = into[key];
      if (existing && typeof existing === 'object' && value && typeof value === 'object') {
        merge(existing as Record<string, unknown>, value as Record<string, unknown>);
      } else {
        into[key] = value;
      }
    }
    return into;
  };
  let merged: Record<string, unknown> = {};
  for (const file of readdirSync(dir).filter(name => name.endsWith('.json'))) {
    merged = merge(merged, JSON.parse(readFileSync(path.join(dir, file), 'utf8')) as Record<string, unknown>);
  }
  const translate = TestBed.inject(TranslateService);
  // The bundle is read off disk, so its type is `unknown` at every leaf; ngx-translate's
  // `TranslationObject` is the same shape with `string` leaves. This is the one place a cast is
  // honest — the file's actual shape is asserted by `terms-are-not-quoted.spec.ts`, which walks it.
  translate.setTranslation('en', merged as Parameters<TranslateService['setTranslation']>[1]);
  translate.use('en');
};

export const P1_CARD: ProfessionalCard = {
  ref: 'p1',
  displayName: 'Akosua Mensah',
  initials: 'AM',
  headline: 'Registered Nutritionist · Metabolic health',
  speciality: 'Nutritionist',
  categoryCode: 'NUTRITION',
  city: 'Accra',
  deliveryModes: ['IN_PERSON', 'ONLINE', 'HOME_VISIT'],
  verification: 'VERIFIED',
  insured: true,
  policeClearance: true,
  yearsPractising: 9,
  responseMinutes: 22,
  rebookRatePct: 68,
  languages: ['English', 'Twi', 'Ga'],
  avatarGradientFrom: '#0D3058',
  avatarGradientTo: '#256ABF',
  rating: 2.5,
  reviewCount: 17,
  fromPriceMinor: 15000,
  fromPaidPriceMinor: 15000,
  hasFreeService: false,
  currency: 'GHS',
  zoneId: 'Africa/Accra',
};

/** The free-service case. `fromPriceMinor` is 0 and the headline must NOT be. */
export const P12_CARD: ProfessionalCard = {
  ...P1_CARD,
  ref: 'p12',
  displayName: 'Abena Owusu',
  initials: 'AO',
  headline: 'Mental wellness coach · Burnout & boundaries',
  speciality: 'Mental Wellness Coach',
  categoryCode: 'WELLNESS',
  deliveryModes: ['ONLINE', 'IN_PERSON'],
  rating: 4.8,
  reviewCount: 4,
  fromPriceMinor: 0,
  fromPaidPriceMinor: 28000,
  hasFreeService: true,
};

/**
 * A listing whose every service is free. **No such professional has ever existed in either
 * estate**, which is why it is constructed rather than measured — and why it matters: the prototype
 * renders `money(Math.min.apply(null, []))`, and `Math.min` of nothing is `Infinity`, so its three
 * sites read "₵Infinity" under a comment claiming that edge is handled (backlog NEW-78).
 */
export const ALL_FREE_CARD: ProfessionalCard = {
  ...P1_CARD,
  ref: 'pfree',
  displayName: 'Free Only',
  initials: 'FO',
  fromPriceMinor: 0,
  fromPaidPriceMinor: null,
  hasFreeService: true,
};

/** A listing publishing nothing at all. Also never seen; told apart from the one above by the marker. */
export const NOTHING_PUBLISHED_CARD: ProfessionalCard = {
  ...P1_CARD,
  ref: 'pnone',
  displayName: 'Nothing Published',
  initials: 'NP',
  fromPriceMinor: null,
  fromPaidPriceMinor: null,
  hasFreeService: false,
};

/** `rating` is null and never 0 for a professional with no reviews — the view has no row for them. */
export const UNRATED_CARD: ProfessionalCard = {
  ...P1_CARD,
  ref: 'punrated',
  displayName: 'Never Reviewed',
  initials: 'NR',
  rating: null,
  reviewCount: 0,
};

export const pageOf = <T>(content: T[], page = 0, size = 6): Page<T> => ({
  content,
  page,
  size,
  totalElements: content.length,
  totalPages: Math.max(1, Math.ceil(content.length / size)),
});

/** The unfiltered facets as the estate answers them today — note the range is 9000–42000 (D100 §6). */
export const FACETS: Facets = {
  categories: [
    { value: 'CARE', label: 'Home & Elder Care', count: 4 },
    { value: 'FITNESS', label: 'Fitness & Movement', count: 5 },
    { value: 'NUTRITION', label: 'Nutrition & Lifestyle', count: 4 },
    { value: 'WELLNESS', label: 'Wellness & Therapy-adjacent', count: 5 },
  ],
  specialities: [
    { value: 'Nutritionist', label: 'Nutritionist', count: 1 },
    { value: 'Mental Wellness Coach', label: 'Mental Wellness Coach', count: 1 },
  ],
  cities: [
    { value: 'Accra', label: 'Accra', count: 9 },
    { value: 'Kumasi', label: 'Kumasi', count: 4 },
  ],
  deliveryModes: [
    { value: 'HOME_VISIT', label: 'HOME_VISIT', count: 12 },
    { value: 'IN_PERSON', label: 'IN_PERSON', count: 13 },
    { value: 'ONLINE', label: 'ONLINE', count: 11 },
  ],
  total: 18,
  minPriceMinor: 9000,
  maxPriceMinor: 42000,
};

export const CATEGORIES: CategoryView[] = [
  {
    code: 'FITNESS',
    name: 'Fitness & Movement',
    blurb: 'Trainers, yoga and pilates teachers, movement and mobility coaches.',
    icon: '🏃',
    sortOrder: 1,
    professionalCount: 5,
    specialities: ['Personal Trainer', 'Yoga Instructor'],
  },
];

/**
 * p1's detail. `verifiedOn` is null on all eighteen seeded professionals — the verification audit
 * trail records nothing about a state that arrived with the seed — and `starDistribution` is ONE
 * STAR FIRST: `[10, 0, 0, 2, 5]` averages 2.53 against the 2.5 the view reports.
 */
export const P1_DETAIL: ProfessionalDetail = {
  card: P1_CARD,
  bio: 'I build eating plans that survive real Ghanaian kitchens and real budgets.',
  countryCode: 'GH',
  credentials: ['BSc Nutrition & Dietetics, University of Ghana'],
  highlights: ['Type-2 diabetes & pre-diabetes'],
  services: [
    {
      ref: 's1b',
      name: 'Follow-up consultation',
      durationMinutes: 30,
      priceMinor: 15000,
      currency: 'GHS',
      description: 'Review of logs, plan adjustment, next targets.',
      active: true,
      sortOrder: 2,
    },
    {
      ref: 's1a',
      name: 'Nutrition assessment (first visit)',
      durationMinutes: 60,
      priceMinor: 28000,
      currency: 'GHS',
      description: 'Full dietary history, body composition, and a written plan you keep.',
      active: true,
      sortOrder: 1,
    },
  ],
  starDistribution: [10, 0, 0, 2, 5],
  verifiedOn: null,
};

/**
 * Two reviews, and the pair is the point: the seed answers `professionalReply: ""` while a review
 * written through the API answers `null`. A template testing only for `null` renders an empty reply
 * block on every seeded review.
 */
export const REVIEWS: ReviewView[] = [
  {
    ref: 'r-seeded',
    authorName: 'Selina Amoah',
    authorInitials: 'SA',
    stars: 5,
    publishedOn: '2026-08-01',
    body: 'Practical, kind and completely unfazed by my kitchen.',
    professionalReply: '',
  },
  {
    ref: 'r-replied',
    authorName: 'Frank Adjetey',
    authorInitials: 'FA',
    stars: 4,
    publishedOn: '2026-07-19',
    body: 'Good plan, slow to start.',
    professionalReply: 'Thank you Frank — glad the second month landed better.',
  },
];
