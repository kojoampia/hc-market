/**
 * The public marketplace read model — catalog's `MarketplaceResource`, as it appears on the wire.
 *
 * <p>These interfaces are transcribed from the RUNNING estate, not from the Java records: every
 * field below was read off `http://127.0.0.1:15509/services/healthconnectcatalog/api/...` on
 * 2026-09-24 against the quality stack at `e331f8e`. That matters because two of them are shapes a
 * reader would guess wrong — `Page` is hand-rolled here rather than Spring's (`page`, not `number`;
 * no `last`/`first`), and `ReviewView.professionalReply` comes back as `""` from the seed and
 * `null` from a review written through the API, so a client that tests it for `null` renders an
 * empty reply block on every seeded review.
 *
 * <p><b>THE THREE PRICE FIELDS ARE NOT INTERCHANGEABLE — `decisions.md` D100.</b> See
 * {@link ProfessionalCard}.
 */

/** catalog's own page envelope. Not Spring's — the field is `page`, and there is no `last`. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * A listing as Discover, Browse and the profile header render it.
 *
 * <p><b>Which number is the headline is a decision, and getting it wrong reintroduces the defect
 * D100 closed.</b>
 *
 * <ul>
 *   <li>{@link fromPriceMinor} — the LITERAL minimum, `0` included. Two seeded professionals (p12,
 *       p13) publish a free service and therefore report `0` here while their cheapest purchasable
 *       service is ₵280 and ₵420. <b>Rendering this as "from ₵0" is the misrepresentation D100 §2(b)
 *       rejected.</b></li>
 *   <li>{@link fromPaidPriceMinor} — the cheapest ACTIVE service that costs something. <b>This is
 *       the headline</b>, it is what the prototype has always computed, and it is what the server's
 *       `maxPriceMinor` filter, both price comparators and the facet range all read. `null` when the
 *       listing sells nothing — which is NOT the same as free.</li>
 *   <li>{@link hasFreeService} — the marker D92 §4 ratified in place of a ₵0 headline. Redundant
 *       with `fromPriceMinor === 0` by construction and published anyway, precisely so that no
 *       client has to infer it.</li>
 * </ul>
 *
 * <p>{@link rating} is `null` and never `0.0` for a professional with no reviews — the
 * `professional_rating` view has no row for them, which is this repository's central rule and the
 * reason a card must not draw five empty stars for "unrated".
 */
export interface ProfessionalCard {
  ref: string;
  displayName: string;
  initials: string;
  headline: string;
  speciality: string;
  categoryCode: string;
  city: string;
  deliveryModes: string[];
  /** `UNVERIFIED` | `PENDING` | `VERIFIED` | `SUSPENDED` — catalog's `VerificationState`. */
  verification: string;
  insured: boolean;
  policeClearance: boolean;
  yearsPractising: number | null;
  responseMinutes: number | null;
  rebookRatePct: number | null;
  languages: string[];
  avatarGradientFrom: string;
  avatarGradientTo: string;
  /** `null` for an unrated professional. Never `0`. */
  rating: number | null;
  reviewCount: number;
  /** The literal minimum, `0` included. Not the headline — see the class javadoc. */
  fromPriceMinor: number | null;
  /** The headline. `null` when nothing purchasable is published. */
  fromPaidPriceMinor: number | null;
  hasFreeService: boolean;
  currency: string;
  zoneId: string;
}

export interface ServiceView {
  ref: string;
  name: string;
  durationMinutes: number | null;
  priceMinor: number;
  currency: string;
  description: string | null;
  active: boolean;
  sortOrder: number | null;
}

export interface ProfessionalDetail {
  card: ProfessionalCard;
  bio: string | null;
  countryCode: string | null;
  credentials: string[];
  highlights: string[];
  services: ServiceView[];
  /** Five buckets, ONE star first: `[10, 0, 0, 2, 5]` on p1 is ten 1★ and five 5★. */
  starDistribution: number[];
  /**
   * `null` on all eighteen seeded professionals, because the verification audit trail (D16) records
   * nothing about a state that arrived with the seed. The badge renders without a date rather than
   * inventing one — which is what the prototype does too.
   */
  verifiedOn: string | null;
}

export interface CategoryView {
  code: string;
  name: string;
  blurb: string;
  icon: string;
  sortOrder: number | null;
  professionalCount: number;
  specialities: string[];
}

export interface FacetValue {
  value: string;
  label: string;
  count: number;
}

export interface Facets {
  categories: FacetValue[];
  specialities: FacetValue[];
  cities: FacetValue[];
  deliveryModes: FacetValue[];
  total: number;
  /** Over `fromPaidPriceMinor`, so the slider and the headline describe one catalogue — D100 §4. */
  minPriceMinor: number | null;
  maxPriceMinor: number | null;
}

export interface AvailabilityDay {
  date: string;
  /** `HH:mm`, in the professional's own zone — `ProfessionalCard.zoneId`, D21. */
  slots: string[];
}

export interface ReviewView {
  ref: string;
  authorName: string;
  authorInitials: string;
  stars: number;
  publishedOn: string;
  body: string;
  /** `""` from the seed, `null` from the API. Both mean "no reply". */
  professionalReply: string | null;
}

/**
 * The Browse query, one field per request parameter catalog accepts.
 *
 * <p><b>Every filter here is SINGLE-VALUED because the server's is</b> — `category`, `speciality`,
 * `mode` and `city` are each one `String` on `MarketplaceResource.professionals`. The prototype
 * renders them as checkbox groups; a client that did the same would have to union the results
 * itself, and the result set is paged by the server, so it would be filtering one page of six.
 * See `decisions.md` D103 §4.
 */
export interface BrowseQuery {
  category?: string;
  speciality?: string;
  mode?: string;
  city?: string;
  maxPriceMinor?: number;
  minRating?: number;
  verifiedOnly?: boolean;
  q?: string;
  sort?: string;
  page?: number;
  size?: number;
}

/**
 * The sort orders the SERVER offers, in the order a menu lists them.
 *
 * <p><b>The prototype's seventh, "Available soonest", is deliberately absent</b> and so is its
 * Discover section of the same name: `ProfessionalCard` carries no availability, no server sort
 * reads one, and computing it in the client is one `/availability` request per card on an
 * unauthenticated public read. D103 §5 argues it; backlog NEW-82 is where it would be added.
 */
export const BROWSE_SORTS = ['recommended', 'rating', 'reviews', 'price-asc', 'price-desc', 'experience', 'response'] as const;

export type BrowseSort = (typeof BROWSE_SORTS)[number];
