import { describe, expect, it } from 'vitest';

import { currencySymbol, money } from './money';

describe('money', () => {
  it('renders whole cedis with no decimal point, as the prototype does', () => {
    // Every one of the 52 seeded services is a whole number of cedis, so this is the case that is
    // on screen today and it must read exactly as the prototype's `money()` does.
    expect(money(28000, 'GHS')).toBe('₵280');
    expect(money(15000, 'GHS')).toBe('₵150');
    expect(money(320000, 'GHS')).toBe('₵3,200');
  });

  it('shows pesewas when there are any, rather than rounding them away', () => {
    // The prototype uses `maximumFractionDigits: 0`, which would render ₵280.50 as "₵281". A price
    // this platform shows is the price `BookingCreator` reads out of the catalogue and charges
    // (D22), so rounding it on the way to the screen is a small lie about somebody's money.
    expect(money(28050, 'GHS')).toBe('₵280.50');
    expect(money(28005, 'GHS')).toBe('₵280.05');
    expect(money(1, 'GHS')).toBe('₵0.01');
  });

  it('renders a genuine zero as ₵0 and a null as nothing at all', () => {
    // THE DISTINCTION THIS WHOLE FUNCTION EXISTS FOR — D100. `0` is a real price (a free service);
    // `null` is "this listing sells nothing", and rendering it as ₵0 would collapse the two exactly
    // as reading an unrated professional as 0.0 collapses "unrated" and "rated badly".
    expect(money(0, 'GHS')).toBe('₵0');
    expect(money(null, 'GHS')).toBeNull();
    expect(money(undefined, 'GHS')).toBeNull();
  });

  it('never divides into a float', () => {
    // 1_000_000_07 pesewas is ₵1,000,000.07. A `minor / 100` with `toFixed(2)` is correct here too,
    // but the integer split is what makes that independent of the double's mantissa.
    expect(money(100000007, 'GHS')).toBe('₵1,000,000.07');
  });

  it('takes the symbol from the currency the server sent, never a constant', () => {
    // `BrokerageConfig.currency` is configurable (`HC_BROKERAGE_CURRENCY`, D57) and every card and
    // service carries its own, so a ₵ compiled into a template renders the wrong symbol on the
    // first estate priced in anything else, with nothing red.
    expect(currencySymbol('GHS')).toBe('₵');
    expect(currencySymbol('ghs')).toBe('₵');
    expect(money(28000, 'USD')).toBe('USD 280');
    expect(currencySymbol(null)).toBe('');
  });

  it('keeps a negative amount negative', () => {
    // D23's compensating ledger entries are negative. Nothing in Stage B renders one, and the sign
    // being dropped by `Math.abs` would be invisible until something did.
    expect(money(-28000, 'GHS')).toBe('-₵280');
  });
});
