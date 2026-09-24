/**
 * Money is minor units and an explicit currency, never a float — `hc-market/CLAUDE.md`.
 *
 * <p>`28000` is ₵280.00 and the 12% brokerage fee is INSIDE it, not added to it. Nothing here
 * divides into a `number` and rounds: the integer is split into cedis and pesewas so a price can
 * never come out a pesewa short of what the ledger holds.
 *
 * <p><b>Two decimals appear only when there are pesewas.</b> The prototype renders whole cedis
 * (`money = ₵ + toLocaleString({maximumFractionDigits: 0})`) and every seeded price is a whole
 * number of cedis, so the two agree on all 52 seeded services — but `maximumFractionDigits: 0`
 * would silently ROUND ₵280.50 to ₵281, and a price this platform shows a customer is the price
 * `BookingCreator` reads out of the catalogue and charges (D22). Truncating it on the way to the
 * screen is a small lie about somebody's money.
 *
 * <p><b>The symbol is not hardcoded.</b> Every card and every service carries its own `currency`,
 * `GHS` on the whole estate today, and `BrokerageConfig.currency` is configurable
 * (`HC_BROKERAGE_CURRENCY`). A `₵` compiled into a template is a client that renders the wrong
 * symbol the day an estate is priced in anything else, with nothing red.
 */

/** The symbols this marketplace has ever needed. Anything else falls back to the ISO code. */
const SYMBOLS: Record<string, string> = {
  GHS: '₵',
};

export const currencySymbol = (currency: string | null | undefined): string => {
  if (!currency) return '';
  return SYMBOLS[currency.toUpperCase()] ?? `${currency.toUpperCase()} `;
};

/**
 * `28000, 'GHS'` → `₵280`; `28050, 'GHS'` → `₵280.50`; `0, 'GHS'` → `₵0`.
 *
 * <p>Returns `null` for a null amount, which is the "this listing sells nothing" case
 * (`fromPaidPriceMinor`) and must render as an absence rather than as a zero — the same rule
 * `rating` follows. A template that renders `money(...) ?? ''` for it is wrong; it must branch.
 */
export const money = (minor: number | null | undefined, currency: string | null | undefined): string | null => {
  if (minor === null || minor === undefined) return null;
  const negative = minor < 0;
  const absolute = Math.abs(Math.trunc(minor));
  const major = Math.floor(absolute / 100);
  const pesewas = absolute % 100;
  const grouped = major.toLocaleString('en-GB');
  const amount = pesewas === 0 ? grouped : `${grouped}.${String(pesewas).padStart(2, '0')}`;
  return `${negative ? '-' : ''}${currencySymbol(currency)}${amount}`;
};
