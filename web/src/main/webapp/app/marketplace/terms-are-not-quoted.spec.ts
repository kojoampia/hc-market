import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * **No screen may state the commission rate or the free-cancellation window.**
 *
 * <p>Both are configurable per estate — `HC_BROKERAGE_COMMISSION_RATE` and
 * `HC_BROKERAGE_FREE_CANCELLATION_HOURS`, `decisions.md` D57 — and no endpoint publishes either to
 * an unauthenticated caller, so a `12%` written into this client is a third copy of a number nobody
 * can reach to change: an estate priced at 15% would advertise 12% with every test green. The
 * prototype, which is the acceptance target, states both. Backlog **NEW-83** is the endpoint that
 * would let a screen say them truthfully.
 *
 * <p>⚠ **THIS FILE EXISTS BECAUSE THE FIRST VERSION OF THE GUARD REACHED ONLY THE ONE SITE NOBODY
 * WOULD USE.** D103 §6 and `CLAUDE.md` both claimed *"two specs assert the absence"*. They asserted
 * over a rendered component's `textContent` in a `TestBed` with **no translation bundle loaded** —
 * and `TranslateDirective` sets `innerHTML` from the translation, so with no bundle ngx-translate
 * returns the KEY and the template's fallback prose is never in the test DOM at all. Measured by
 * mutation:
 *
 * <pre>
 *   the 12% sentence into discover.html's abmTranslate FALLBACK text   -> 267/267 green
 *   the same sentence into i18n/en/marketplace.json                    -> 267/267 green
 *   a plain untranslated &lt;p&gt;… 12% brokerage fee.&lt;/p&gt;              -> 1 red  (the control)
 * </pre>
 *
 * <p>So the two sites a *"restore parity with the prototype"* edit would actually touch — the
 * translation bundle, and a template's fallback — were both invisible, and the guard was inside the
 * very decision whose §9(a) is about not loosening guards. **A check whose reach depends on where
 * the text happens to live is not a check.**
 *
 * <p>This reads the FILES: every `i18n/en/*.json` value and every template under `app/`. The
 * rendered-DOM assertions in `discover.spec.ts` and `professional.spec.ts` are kept and now load the
 * real bundle, so the two halves fail for different reasons — one about what is written, one about
 * what a reader sees.
 *
 * <p>⚠ **AND THE FIRST VERSION OF *THIS* FILE MISSED ONE OF THE PROTOTYPE'S OWN SIX SENTENCES, IN
 * BOTH ARMS** (D103 §14, found at re-review by mutation). It claimed the two scopes were *"every
 * place a number can be typed"*, and it was wrong twice over:
 *
 * <pre>
 *   "Free up to 24 hours before the session"  (the prototype's Cancellation row)  -> 279/279 green
 *   the same sentence, and five others, in app/layouts/footer/footer.html         -> 279/279 green
 * </pre>
 *
 * The first escaped because the hours arm required `cancel|cancellation|refund` and that sentence
 * carries the window WITHOUT the noun — the prototype puts the word "Cancellation" in the label cell
 * beside it, so the value a bundle would hold says only `free`. The second escaped because the walk
 * stopped at `app/marketplace` while the footer renders on all three public screens. `free` is in
 * both alternations now and the walk is `app/`-wide; of the six rate-and-window sentences the
 * prototype writes, the original patterns caught five.
 *
 * <p>Two limits that remain, stated rather than left as an empty column — a `—` in a column whose
 * other rows carry numbers is not neutral.
 *
 * <p><b>ONE: the template arm reads `.html` only, and widening it needs a comment stripper rather
 * than a wider glob.</b> Excluding `*.spec.ts` (a class of file, not a named one — a sentence in a
 * spec cannot reach a visitor) is enough to keep this file's own probe strings out, and the widening
 * was then measured over the remaining 138 files: it has exactly one hit, and the hit is **correct
 * code**. `app/marketplace/money.ts`'s javadoc says *"`28000` is ₵280.00 and the 12% brokerage fee is
 * INSIDE it"* — which is `CLAUDE.md`'s own sentence about the money model, in a comment, stating a
 * fact rather than showing a visitor a number. So a raw `.ts` scan is red on a correct tree, and
 * doing it properly means what the CI checks in this repository already do: strip comments first
 * (`.github/checks/strip-comments.awk`, whose Java syntax covers TypeScript) — with the warning D77
 * earned attached, that a hand-rolled private stripper is how eight of those checks failed open.
 * Until then a rate composed in a component's TypeScript is seen by the DOM half on the two screens
 * that have one, and by nothing on a screen that does not.
 *
 * <p><b>TWO: the two scopes are asymmetric on purpose.</b> Bundles are read estate-wide, because a
 * translation is never scoped to a screen; templates from `app/` down, because that is every
 * template this client renders.
 */
describe('the brokerage terms are not quoted on any screen', () => {
  const WEBAPP = 'src/main/webapp';
  const I18N = path.join(WEBAPP, 'i18n/en');
  const APP = path.join(WEBAPP, 'app');

  /**
   * A commission rate or a cancellation window as a reader would meet it.
   *
   * <p>Deliberately NOT a bare `/\d+\s*%/`: `rebookRatePct` renders "88% rebook" on every profile
   * and is a fact about one professional the API publishes, not a platform term. So the percentage
   * arm requires a word that makes it the platform's fee, and the hours arm requires a word that
   * makes it a cancellation window rather than a duration.
   *
   * <p>`free` is one of those words and was missing until D103 §14 — the prototype's own
   * *"Free up to 24 hours before the session"* states the window with no other noun in the sentence.
   * It is NOT safe to drop the noun requirement instead: a bare `/\d+\s*hours?/` is red on
   * `i18n/en/reset.json`'s *"a password request is only valid for 24 hours"*, which is correct copy.
   * Measured over every bundle value and every `app/` template: four patterns, zero hits.
   */
  const FORBIDDEN = [
    /\d+\s*(%|per\s*cent)[^.]{0,40}\b(brokerage|commission|fee|platform|take)/i,
    /\b(brokerage|commission|fee|platform)\b[^.]{0,40}\d+\s*(%|per\s*cent)/i,
    /\d+\s*(hour|hours|hrs)\b[^.]{0,60}\b(cancel|cancellation|refund|free)/i,
    /\b(cancel|cancellation|refund|free)\w*\b[^.]{0,60}\d+\s*(hour|hours|hrs)\b/i,
  ];

  const offendingIn = (label: string, text: string): string[] =>
    FORBIDDEN.flatMap(pattern => {
      const hit = pattern.exec(text);
      return hit ? [`${label}: ${hit[0].trim()}`] : [];
    });

  /** Every string value in a nested translation bundle, with its dotted key. */
  const valuesOf = (node: unknown, prefix: string): { key: string; value: string }[] => {
    if (typeof node === 'string') return [{ key: prefix, value: node }];
    if (node === null || typeof node !== 'object') return [];
    return Object.entries(node as Record<string, unknown>).flatMap(([key, child]) => valuesOf(child, prefix ? `${prefix}.${key}` : key));
  };

  const bundles = readdirSync(I18N).filter(file => file.endsWith('.json'));

  const templates = ((): string[] => {
    const walk = (dir: string): string[] =>
      readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) return walk(full);
        return entry.name.endsWith('.html') ? [full] : [];
      });
    return walk(APP).sort();
  })();

  const relative = (file: string): string => path.relative(WEBAPP, file);

  it('has bundles and templates to read', () => {
    // THE CONTROL. Every assertion below is satisfied by a walk that found nothing, which is how the
    // version this file replaces passed over text it had never loaded.
    expect(bundles.length, 'no translation bundle found').toBeGreaterThan(0);
    expect(bundles).toContain('marketplace.json');
    // NAMED, not counted. A floor under the real number lets the walk lose a file in silence, and a
    // floor equal to it is red on the next screen Stage C adds. These four are the property: the
    // three public screens, and one template OUTSIDE app/marketplace — the scope D103 §14 widened.
    const found = templates.map(relative);
    expect(found, 'Discover was not walked').toContain('app/marketplace/discover/discover.html');
    expect(found, 'Browse was not walked').toContain('app/marketplace/browse/browse.html');
    expect(found, 'the public profile was not walked').toContain('app/marketplace/professional/professional.html');
    expect(found, 'the walk did not leave app/marketplace').toContain('app/layouts/footer/footer.html');
    const joined = templates.map(file => readFileSync(file, 'utf8')).join('\n');
    expect(joined, 'the templates were not actually read').toContain('abmTranslate');
  });

  it('matches every rate and window sentence the prototype actually writes', () => {
    // THE POSITIVE CONTROL FOR THE PATTERN, and it is not decoration: a pattern that matched nothing
    // would make every assertion below pass over any text at all. These are the prototype's own SIX
    // sentences — every one of them, read off docs/Abofonsa_BridgeCare_Marketplace.html — because
    // they are what a parity edit would paste in and the first version of this file caught five.
    // The third is the one that escaped: the window with no cancellation noun anywhere in it.
    expect(offendingIn('probe', 'We hold the fee and release it after the session, minus a 12% brokerage fee.')).not.toEqual([]);
    expect(offendingIn('probe', 'Cancel free up to 24 hours before.')).not.toEqual([]);
    expect(offendingIn('probe', 'Free up to 24 hours before the session')).not.toEqual([]);
    expect(offendingIn('probe', 'Free cancellation up to 24 hours before.')).not.toEqual([]);
    expect(offendingIn('probe', 'More than 24 hours away — this cancellation is free and nothing will be charged.')).not.toEqual([]);
    expect(offendingIn('probe', 'Less than 24 hours away — a 50% late-cancellation fee applies')).not.toEqual([]);
    expect(offendingIn('probe', 'Our platform takes 12 per cent.')).not.toEqual([]);
  });

  it('does not refuse a percentage that belongs to a professional', () => {
    // THE NEGATIVE CONTROL. `rebookRatePct` is a fact about one listing that the API publishes and
    // every profile renders — "88% rebook". A guard that refused it would be red on correct code,
    // which is how a ban gets deleted by the next person who meets it.
    expect(offendingIn('probe', '88% rebook')).toEqual([]);
    expect(offendingIn('probe', 'Replies in about 16 minutes')).toEqual([]);
    expect(offendingIn('probe', '9 years practising')).toEqual([]);
    // And the three the widened `free` arm has to keep letting through. The first is real copy in
    // `i18n/en/reset.json`; the other two are the card's own price states (D100, D103 §2), which say
    // `free` beside no window at all.
    expect(offendingIn('probe', 'a password request is only valid for 24 hours')).toEqual([]);
    expect(offendingIn('probe', 'Free service available')).toEqual([]);
    expect(offendingIn('probe', 'Free')).toEqual([]);
  });

  it('states no rate and no window in any translation bundle', () => {
    // The site the old guard could not see: ngx-translate returns the KEY when no bundle is loaded,
    // so nothing in a TestBed ever reads this file.
    const offenders = bundles.flatMap(file =>
      valuesOf(JSON.parse(readFileSync(path.join(I18N, file), 'utf8')), '').flatMap(entry =>
        offendingIn(`${file} ${entry.key}`, entry.value),
      ),
    );
    expect(offenders, 'a brokerage term is quoted in a translation — see NEW-83').toEqual([]);
  });

  it('states no rate and no window in any template the client renders', () => {
    // The other site the old guard could not see: an `abmTranslate` element's fallback text is
    // replaced by `innerHTML` at run time, so it is never in the test DOM — and it is exactly where
    // somebody pasting from the prototype would put a sentence. `app/`-wide since D103 §14: the
    // footer renders on all three public screens and was outside the scope this walk used to have.
    const offenders = templates.flatMap(file => offendingIn(relative(file), readFileSync(file, 'utf8')));
    expect(offenders, 'a brokerage term is quoted in a template — see NEW-83').toEqual([]);
  });
});
