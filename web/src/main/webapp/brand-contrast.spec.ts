import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/**
 * The brand's colour pairings, checked rather than eyeballed.
 *
 * <p>"Never white text on gold — 2.74:1, fails AA" is stated in `hc-market/CLAUDE.md`, in the
 * workspace guide and in `decisions.md` D90 §3, and until this file nothing in any of the four
 * products could see it being broken. A rule that only exists in prose is a claim; this reads the
 * tokens off disk and computes the ratio.
 *
 * <p><b>Why the source is `_hc-tokens.scss` and not a TypeScript constant.</b> The stylesheets are
 * what the browser gets. A duplicated palette in `.ts` would be the configuration checked against
 * itself — the failure mode hc-admin's `check-built-assets.mjs` names — and the two copies would
 * drift with nothing able to say so.
 *
 * <p><b>Why this file sits at the webapp root rather than beside the stylesheets.</b> hc-admin put
 * its equivalent in `content/scss/` and that spec became the NINTH file its production build
 * published to the browser — found only when a guard listed the directory instead of counting one
 * extension. `angular.json`'s `ignore: ["scss/**"]` would cover a spec placed there, but a source
 * file that must not ship is better kept out of the directory that ships at all.
 */
describe('brand contrast', () => {
  const TOKENS = 'src/main/webapp/content/scss/_hc-tokens.scss';
  const source = readFileSync(TOKENS, 'utf8');

  /** WCAG 2.1 relative luminance. Six-digit hex only — anything else throws rather than guesses. */
  const luminance = (hex: string): number => {
    if (!/^#[0-9a-f]{6}$/i.test(hex)) throw new Error(`not a six-digit hex colour: ${hex}`);
    const channels = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255);
    const linear = (v: number): number => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);
    const [r, g, b] = channels.map(linear);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };

  /** WCAG 2.1 contrast ratio, 1:1 to 21:1. */
  const contrast = (a: string, b: string): number => {
    const [lighter, darker] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (lighter + 0.05) / (darker + 0.05);
  };

  /** Every `$abm-<name>: #rrggbb;` in the token file, by name without the prefix. */
  const tokens = new Map<string, string>(
    [...source.matchAll(/^\$abm-([a-z0-9-]+):\s*(#[0-9a-f]{6});/gim)].map(([, name, hex]) => [name, hex.toLowerCase()]),
  );

  /** The `$abm-text-on` map: surface name -> the one foreground token it may carry. */
  const pairings = ((): Map<string, string> => {
    const block = /\$abm-text-on:\s*\(([\s\S]*?)\);/.exec(source);
    expect(block, `${TOKENS} declares no $abm-text-on map`).not.toBeNull();
    return new Map([...block![1].matchAll(/'([a-z0-9-]+)':\s*\$abm-([a-z0-9-]+),/gi)].map(([, surface, fg]) => [surface, fg]));
  })();

  /**
   * Tokens that are deliberately not surfaces, so the derivation below can demand a pairing for
   * everything else. Each is a line, a body colour or a muted foreground — never something text is
   * set ON. Adding a colour token without adding it here or to the map is red, which is the point:
   * a test whose coverage has to be widened by hand silently stops covering things.
   */
  const NOT_A_SURFACE = new Set(['ink', 'grey', 'line']);

  it('reads the tokens it is checking', () => {
    // The control for every assertion below: a regex that matched nothing would satisfy each of
    // them vacuously.
    expect(tokens.get('navy')).toBe('#0d3058');
    expect(tokens.get('gold')).toBe('#c59437');
    expect(tokens.get('cream')).toBe('#f7f4ee');
    expect(tokens.size).toBeGreaterThanOrEqual(12);
    expect(pairings.size).toBeGreaterThanOrEqual(12);
  });

  it('reproduces the published 2.74:1 for white on gold', () => {
    // THE NEGATIVE CONTROL, and it is the reason to trust the rest. hc-market/CLAUDE.md publishes
    // 2.74 for this pair; a contrast function that agreed with nothing would still pass every
    // "at least 4.5" assertion by returning 21.
    expect(contrast('#ffffff', tokens.get('gold')!)).toBeCloseTo(2.74, 2);
    expect(contrast('#ffffff', tokens.get('gold')!)).toBeLessThan(4.5);
  });

  it.each([...pairings.entries()])('pairs %s with a foreground that reaches AA', (surface, foreground) => {
    const background = tokens.get(surface);
    const text = tokens.get(foreground);
    expect(background, `$abm-text-on names surface '${surface}', which is not a declared token`).toBeDefined();
    expect(text, `$abm-text-on names foreground '${foreground}', which is not a declared token`).toBeDefined();
    expect(contrast(background!, text!), `${surface} (${background!}) on ${foreground} (${text!})`).toBeGreaterThanOrEqual(4.5);
  });

  it('gives every colour token either a pairing or a reason to have none', () => {
    const unpaired = [...tokens.keys()].filter(name => !pairings.has(name) && !NOT_A_SURFACE.has(name));
    expect(unpaired, 'these colour tokens are neither a surface with a foreground nor listed as NOT_A_SURFACE').toEqual([]);
  });

  it('never pairs gold with a light surface, in either direction', () => {
    // The rule is wider than the sentence that states it. Gold fails as a FOREGROUND too — 2.74 on
    // white, 2.50 on cream — so "white on gold" and "gold on white" are the same defect twice.
    const gold = tokens.get('gold')!;
    for (const light of ['white', 'cream', 'gold-050']) {
      expect(contrast(gold, tokens.get(light)!), `gold against ${light}`).toBeLessThan(4.5);
      expect(pairings.get(light), `${light} must not take gold as its foreground`).not.toBe('gold');
    }
    expect(pairings.get('gold'), 'gold must not take a light foreground').not.toBe('white');
  });
});
