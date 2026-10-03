import { mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

import { getValue } from '@ngx-translate/core';

import { mergedEnglishBundle } from './marketplace.fixtures';

/**
 * **Every translation key this client writes as a literal must resolve to a sentence.**
 *
 * <p>`ngx-translate` has no notion of a key being wrong. Asked for one no bundle defines it calls the
 * configured {@link MissingTranslationHandler} and, failing that, returns the key itself — measured in
 * `@ngx-translate/core` 18.0.0, `getParsedResultForKey`: `return res !== undefined ? res : key`. And
 * `TranslateDirective` sets `innerHTML` from whatever comes back. So a typo in a key is not an error,
 * a warning or a blank: it is a dotted identifier rendered **in the position the sentence should
 * occupy**, to whoever is reading the page.
 *
 * <p>⚠ **WHAT A VISITOR ACTUALLY SEES IS NOT THE BARE KEY, AND backlog NEW-91 SAID IT WAS** — see
 * `decisions.md` D109 §2. This client **does** configure a handler:
 * `MissingTranslationHandlerImpl` in `app/config/translation.config.ts` returns
 * `translation-not-found[<key>]`. Measured by rendering `LoadState` twice against the real bundle with
 * one unknown key:
 *
 * <pre>
 *   provideTranslateService()                 -> "marketplace.state.failed.NO_SUCH_KEY"
 *   provideTranslation()'s handler (the app)  -> "translation-not-found[marketplace.state.failed.NO_SUCH_KEY]"
 * </pre>
 *
 * The first is what every spec in this client sees, because every spec calls bare
 * `provideTranslateService()`. The second is what ships. Both are a dotted identifier where a sentence
 * belongs, so the defect is unchanged — but the handler already existing is why *"wire a
 * `MissingTranslationHandler`"* is not available as the fix: there is one, and it is part of the
 * symptom rather than a guard against it.
 *
 * <p><b>WHY NOTHING ELSE CATCHES THIS.</b> `LoadState.failedTitleKey`'s default was changed to
 * `marketplace.state.failed.NO_SUCH_KEY` on the committed tree, nothing else touched, and the suite
 * answered **47 files / 312 tests, all green** — twice, independently measured at NEW-60's review and
 * again here. That default is the failure headline on all three public screens. The three screen specs
 * do load the real bundle, but they assert prose they expect to be *present*
 * (`toContain('could not be reached')`), which passes or fails on the one key it names and says
 * nothing about any other key on the page. `terms-are-not-quoted.spec.ts` reads these same files, and
 * its subject is a **forbidden** sentence — *a key with no value is exactly what a "must not contain"
 * guard cannot see.*
 *
 * <p><b>THE RESOLVER IS THE LIBRARY'S OWN, AND THAT IS NOT CONVENIENCE.</b> The first version of this
 * walk split keys on `.` and indexed the merged tree, and reported **seven unresolved keys on a
 * correct tree** — `global.form.username.label`, `login.form.password.placeholder` and five more. The
 * tree was right and the walk was wrong: the generated JHipster bundles hold **flat keys that contain
 * dots**, `"username.label": "Username"` nested under `global.form`, and ngx-translate's `getValue`
 * accumulates segments until one matches rather than splitting once. Importing `getValue` makes
 * "resolves" mean here exactly what it means at run time, by construction instead of by transcription
 * — and the day the library's algorithm changes, the two controls below go red rather than this walk
 * inventing failures.
 *
 * <p><b>WHAT IT COVERS, AND WHAT IT CANNOT.</b> A key assembled at run time cannot be read off a
 * file: `[abmTranslate]="'health.indicator.' + componentHealth.key"` and
 * `{{ 'marketplace.mode.' + facet.value | translate }}` name a *family*, not a key. The decision
 * (D109 §4) is to cover the literals, check the one thing a prefix does state — that the subtree
 * exists — and **stop there** rather than widen until the spec is unreadable. The seventeen
 * non-literal sites surveyed are enumerated in D109 §3; `theLimit` below proves the limit is where
 * this comment says it is, and `D109 §5` names what would catch the rest if anyone ever needs it.
 *
 * <p><b>ENGLISH ONLY, and that is the whole estate.</b> `.yo-rc.json` declares
 * `languages: ["en"]` and `i18n/` holds no other directory, so `i18n/en` is not a sample of the
 * bundles — it is all of them. The day a second language lands this walk reads one of several and
 * silently stops being estate-wide: the fix then is to walk every `i18n/*` directory and keep
 * `fallbackLang` in mind, since a key present in `en` and absent in the new language resolves
 * through the fallback rather than failing.
 *
 * <p><b>Why this file sits under `app/marketplace/` while walking `app/`.</b> The same reason its
 * neighbour does: `terms-are-not-quoted.spec.ts` is the other file-reading guard in this client and a
 * reader who finds one should find both. `app/shared/language/`, next to `TranslateDirective`, is the
 * honest alternative and was rejected only on discoverability.
 */
describe('every translation key this client writes resolves', () => {
  const WEBAPP = 'src/main/webapp';
  const APP = path.join(WEBAPP, 'app');

  const bundle = mergedEnglishBundle();

  /** A key resolves when it yields a STRING. A key resolving to a subtree renders `[object Object]`. */
  const resolves = (key: string): boolean => typeof getValue(bundle, key) === 'string';
  /** A prefix states only that a subtree exists — never which members of it do. */
  const isSubtree = (key: string): boolean => {
    const found = getValue(bundle, key);
    return found !== null && typeof found === 'object';
  };

  const relative = (file: string): string => path.relative(WEBAPP, file);

  /**
   * Two independent listings of the same tree, compared.
   *
   * <p>This is the anti-shrink property, and a floor under a count is NOT it: D103 §14's
   * `templates.length > 4` against a real 6 let `footer.html` fall out of a walk in silence, and
   * `CLAUDE.md` names that as the lesson. A recursive `withFileTypes` descent and node's own
   * `recursive: true` are different code paths over the same directory, so a descent that loses a
   * branch disagrees with the listing. Narrowing *both* is caught by the named files in `theWalk`.
   */
  const descend = (dir: string, keep: (name: string) => boolean): string[] =>
    readdirSync(dir, { withFileTypes: true })
      .flatMap(entry => {
        const full = path.join(dir, entry.name);
        return entry.isDirectory() ? descend(full, keep) : keep(entry.name) ? [full] : [];
      })
      .sort();

  const listing = (dir: string, keep: (name: string) => boolean): string[] =>
    readdirSync(dir, { recursive: true, encoding: 'utf8' })
      .filter(entry => keep(path.basename(entry)))
      .map(entry => path.join(dir, entry))
      .sort();

  // A spec's own probe keys must not be demanded to resolve. Excluded as a CLASS of file rather than
  // by name — `terms-are-not-quoted.spec.ts`' reasoning, and this very file would otherwise be red.
  const isTemplate = (name: string): boolean => name.endsWith('.html');
  const isSource = (name: string): boolean => name.endsWith('.ts') && !name.endsWith('.spec.ts');
  const isRouteFile = (file: string): boolean => /\.routes?\.ts$/.test(file);

  const templates = descend(APP, isTemplate);
  const sources = descend(APP, isSource);
  const read = (file: string): string => readFileSync(file, 'utf8');

  type Site = { file: string; key: string };
  const sitesIn = (files: string[], pattern: RegExp, group = 1): Site[] =>
    files.flatMap(file => [...read(file).matchAll(pattern)].map(hit => ({ file: relative(file), key: hit[group] })));

  /**
   * WALK 2a's subject, and the *input NAMES* it discovers are what makes WALK 1 precise.
   *
   * <p>The convention in this client is that a component input whose name ends in `Key` holds a
   * translation key — `LoadState`'s seven. Deriving the names here rather than listing them means a
   * new such input is covered the day it exists, and means WALK 1 can check the literals callers pass
   * to it without guessing which attributes are keys. **It also enforces the convention**: a `*Key`
   * input that is NOT a translation key would be red here, and that is the right direction — the
   * suffix is load-bearing for this spec's reach and must keep meaning one thing.
   */
  const KEY_INPUT = /\b([A-Za-z]*Key)\s*=\s*input(?:\.required)?\(\s*'([^']+)'/g;
  const keyInputs = sitesIn(sources, KEY_INPUT, 2);
  const keyInputNames = [...new Set(sitesIn(sources, KEY_INPUT, 1).map(site => site.key))];

  const routeFiles = sources.filter(isRouteFile);

  /**
   * EVERY SHAPE, DEFINED EXACTLY ONCE AS DATA, because two copies of one expression is the drift this
   * repository keeps finding — the four walks below, the shape guard and the limit case all read this
   * map rather than a transcription of it. Holding the patterns as data rather than as closures is
   * what lets the limit case re-run **the shipped expressions** against a synthetic file.
   *
   * <p>A shape whose pattern stops matching takes its whole walk with it silently — `unresolved([])`
   * is `[]` — so each one has a named representative in `theShapes`.
   */
  type Shape = { files: string[]; patterns: RegExp[]; group?: number; dedupe?: boolean };
  const SHAPES: Record<string, Shape> = {
    // WALK 1 — the static attribute, 213 of them, and the population D105 converted five of to bound
    // inputs.
    'abmTranslate attribute': { files: templates, patterns: [/\babmTranslate="([^"{}]+)"/g] },
    // WALK 1 — `{{ 'k' | translate }}` and `[attr.x]="'k' | translate"`. The literal form only; a
    // prefixed concatenation piped through `translate` is WALK 3's.
    'translate pipe': { files: templates, patterns: [/'([A-Za-z][\w.]*)'\s*\|\s*translate/g] },
    // WALK 1 — `[abmTranslate]="'k'"`. None today, and cheap to cover so the first one is not a gap;
    // it has no representative in `theShapes` for exactly that reason, which is stated there.
    'bound literal': { files: templates, patterns: [/\[abmTranslate\]="'([A-Za-z][\w.]*)'"/g] },
    // WALK 1 — the literals callers pass to a discovered `*Key` input, `emptyTitleKey="marketplace.…"`.
    // These are PLAIN ATTRIBUTES, not `abmTranslate`, so the attribute shape cannot see them: 22 sites
    // across Browse, Discover, the public profile and both account screens. One pattern per discovered
    // name, so the set is derived from the TypeScript rather than guessed from the markup.
    '*Key attribute': {
      files: templates,
      patterns: keyInputNames.map(name => new RegExp(`\\b${name}="([^"{}]+)"`, 'g')),
    },
    // WALK 2a — what a component falls back to when no caller overrides it. The NEW-91 mutation's home.
    '*Key input default': { files: sources, patterns: [KEY_INPUT], group: 2 },
    // WALK 2b — `title` becomes `document.title` through `AppPageTitleStrategy`, so an unresolved one
    // is a dotted identifier in the browser tab, the bookmark and the history entry.
    'route title': { files: routeFiles, patterns: [/^\s*title:\s*'([^']+)'/gm] },
    // WALK 2b — `data.errorMessage` becomes the error page's sentence through `Error.ngOnInit`, and
    // that page is reached by `errorRoute`'s `path: '**'` — EVERY unserved URL in the client — which
    // makes `error.http.404` the most-reached key here and the one nothing else covers at all.
    'route error key': { files: routeFiles, patterns: [/^\s*errorMessage:\s*'([^']+)'/gm] },
    // WALK 3 — a key ASSEMBLED from a literal prefix. The prefix is all that is readable; see the walk
    // itself for why that is checked weakly rather than not at all.
    'assembled prefix': {
      files: templates,
      patterns: [/\[abmTranslate\]="'([A-Za-z][\w.]*)\.'\s*\+/g, /'([A-Za-z][\w.]*)\.'\s*\+\s*[^|"]+\|\s*translate/g],
      dedupe: true,
    },
  };

  /** The one place a shape becomes a list of sites. `over` exists so the limit case can redirect it. */
  const sitesOfShape = (name: string, over?: string[]): Site[] => {
    const shape = SHAPES[name];
    const found = shape.patterns.flatMap(pattern => sitesIn(over ?? shape.files, pattern, shape.group ?? 1));
    return shape.dedupe ? found.filter((site, i, all) => all.findIndex(other => other.key === site.key) === i) : found;
  };
  const sitesOf = (...shapes: string[]): Site[] => shapes.flatMap(shape => sitesOfShape(shape));

  /**
   * ⚠ **Both route shapes are scoped to `*.route.ts` / `*.routes.ts` rather than to all sources**,
   * because `title:` is an ordinary property name — `problem-details.ts` declares `title: string`, and
   * a `title: 'Some plain words'` elsewhere would make this file red on correct code. Route files are
   * where the generator and this client both put translation keys under those two names.
   *
   * <p>⚠ **The one literal key outside every shape above is `AppPageTitleStrategy`'s
   * `pageTitle ??= 'global.title'` fallback** — a literal in a `.ts` that is not a route file and not
   * an argument to the `get` call, so neither the route shapes nor a `.get('…')` pattern sees it. It
   * *happens* to be covered, because `navbar.html` renders `abmTranslate="global.title"` and WALK 1
   * reads that — **a coincidence, not a guarantee**, stated rather than left as a silent gap: deleting
   * that one `<span>` would un-cover the title of every page with no route title of its own.
   */
  const ROUTE_SHAPES = ['route title', 'route error key'];

  const unresolved = (sites: Site[]): string[] => sites.filter(site => !resolves(site.key)).map(site => `${site.file}: ${site.key}`);

  it('resolves a nested key, resolves a FLAT DOTTED key, and resolves NOTHING that is absent', () => {
    // THE CONTROLS, and the third is the one holding the file up. Every assertion below is satisfied
    // by a resolver that answers a string for anything, which is how a walk reports a clean tree
    // having checked nothing.
    expect(getValue(bundle, 'marketplace.state.failed.title'), 'the nested case').toBe('The marketplace could not be reached');
    // The case the hand-rolled resolver got wrong, and the reason `getValue` is imported: ONE key
    // called `username.label`, nested under `global.form`, with a dot inside its own name.
    expect(getValue(bundle, 'global.form.username.label'), 'the flat dotted case').toBe('Username');
    // THE NEGATIVE CONTROL. Without it this whole file passes against a bundle it never loaded.
    expect(resolves('marketplace.state.failed.NO_SUCH_KEY'), 'an absent key must not resolve').toBe(false);
    expect(resolves('nothing.like.this.exists.anywhere'), 'an absent root must not resolve').toBe(false);
    // A key that lands on a SUBTREE is not resolved either — it renders `[object Object]`.
    expect(isSubtree('marketplace.verification'), 'the subtree control').toBe(true);
    expect(resolves('marketplace.verification'), 'a subtree is not a sentence').toBe(false);
  });

  it('walked the whole of app/, by two derivations and by name', () => {
    // Half one: two different directory APIs over the same tree must agree. A descent that loses a
    // branch is red here even though nothing about the number changed.
    expect(templates, 'the template descent disagrees with a recursive listing').toEqual(listing(APP, isTemplate));
    expect(sources, 'the source descent disagrees with a recursive listing').toEqual(listing(APP, isSource));
    // Half two: NAMED files, never a count. Narrowing both derivations together is only visible here,
    // and the four are chosen to pin the reach rather than to sample it — one public screen, one
    // template OUTSIDE app/marketplace (the scope D103 §14 had to widen), one admin screen, one
    // account screen.
    const found = templates.map(relative);
    expect(found, 'Discover was not walked').toContain('app/marketplace/discover/discover.html');
    expect(found, 'the walk did not leave app/marketplace').toContain('app/layouts/footer/footer.html');
    expect(found, 'the admin screens were not walked').toContain('app/admin/health/health.html');
    expect(found, 'the account screens were not walked').toContain('app/account/activation/activation.html');
    const code = sources.map(relative);
    expect(code, 'LoadState was not walked').toContain('app/marketplace/load-state/load-state.ts');
    expect(code, 'the marketplace routes were not walked').toContain('app/marketplace/marketplace.routes.ts');
    expect(code, 'the account routes were not walked').toContain('app/account/account-lifecycle.routes.ts');
    // And the files were READ, not merely listed.
    expect(templates.map(read).join('\n')).toContain('abmTranslate');
  });

  it('still matches every shape it claims to read', () => {
    // A pattern that stops matching takes its whole walk with it and nothing else here would notice:
    // `unresolved([])` is `[]`. MEASURED — with the `abmTranslate` pattern broken, all four walks below
    // stayed GREEN and only this assertion fired. One NAMED representative per shape, each chosen
    // because it is a site somebody would have to delete the screen to remove.
    const keysOf = (shape: string): string[] => sitesOfShape(shape).map(site => site.key);
    // `marketplace.scope.body` is the one piece of prose CLAUDE.md calls a hard boundary, and D103 §14
    // records it rendering its own key to a visitor with everything green.
    expect(keysOf('abmTranslate attribute'), 'the attribute shape matched nothing').toContain('marketplace.scope.body');
    expect(keysOf('translate pipe'), 'the pipe shape matched nothing').toContain('marketplace.browse.filters.searchPlaceholder');
    expect(keysOf('*Key attribute'), 'the *Key attribute shape matched nothing').toContain('activate.state.nokey.title');
    expect(keysOf('route title'), 'the route-title shape matched nothing').toContain('marketplace.professional.pageTitle');
    expect(keysOf('route error key'), 'the route-error-key shape matched nothing').toContain('error.http.404');
    expect(keysOf('assembled prefix'), 'the prefix shape matched nothing').toContain('marketplace.verification');
    // `bound literal` has NO representative and that is deliberate, not an omission: there is no
    // `[abmTranslate]="'literal'"` anywhere in this client, so any assertion here would be red on a
    // correct tree. It is covered so the FIRST one is not a gap, and this comment is why its absence
    // from this list is not evidence the shape was forgotten.
    expect(keysOf('bound literal'), 'a bound literal appeared — give this shape a representative').toEqual([]);
    // And the map is the one definition of every shape, so a shape deleted from it is red here rather
    // than quietly dropping out of a walk.
    expect(Object.keys(SHAPES).sort()).toEqual(
      [
        '*Key attribute',
        '*Key input default',
        'abmTranslate attribute',
        'assembled prefix',
        'bound literal',
        'route error key',
        'route title',
        'translate pipe',
      ].sort(),
    );
    // LoadState's seven BY NAME. This is the set the §3 mutation lives in, and all seven render on the
    // three public screens. `toContain` rather than an exact set: a new `*Key` input elsewhere must
    // enlarge this, not redden it.
    expect(keyInputNames).toEqual(
      expect.arrayContaining([
        'emptyTitleKey',
        'emptyDetailKey',
        'loadingTitleKey',
        'loadingDetailKey',
        'failedTitleKey',
        'failedDetailKey',
        'failedRetryKey',
      ]),
    );
  });

  it('resolves every literal key a template names', () => {
    // WALK 1 — what is WRITTEN in markup, across 246 literal sites, including the 22 `*Key` overrides
    // that are plain attributes rather than `abmTranslate`.
    const sites = sitesOf('abmTranslate attribute', 'translate pipe', 'bound literal', '*Key attribute');
    expect(unresolved(sites), 'a template names a key no bundle defines — it will render as itself').toEqual([]);
  });

  it('resolves every literal key a component input defaults to', () => {
    // WALK 2a — what a component FALLS BACK TO when no caller overrides it, which is the three public
    // screens for all seven of LoadState's. This is the walk the NEW-91 mutation is red on; it is a
    // separate assertion from WALK 1 on purpose, because an aggregate firing once cannot say which of
    // the two mechanisms still works.
    expect(unresolved(sitesOf('*Key input default')), 'a component input defaults to a key no bundle defines').toEqual([]);
  });

  it('resolves every literal key a route definition carries', () => {
    // WALK 2b — both reach `translateService.get`: `title` into `document.title`, `data.errorMessage`
    // into the error page's sentence. `error.http.404` is covered by nothing else in this file and the
    // page it renders on is the one every unserved URL in the client reaches.
    expect(unresolved(sitesOf(...ROUTE_SHAPES)), 'a route names a key no bundle defines').toEqual([]);
  });

  it('finds a subtree for every prefix a key is assembled from', () => {
    // WALK 3, and it is deliberately WEAKER than the others rather than as strong: a prefix states
    // that a family exists, never which members of it do. `'marketplace.mode.' + facet.value` is red
    // here if `marketplace.mode` is renamed away — the realistic failure, since the subtree and its
    // members move together — and silent if one delivery mode is dropped from the bundle. Checking
    // the members would mean this spec knowing the catalogue's enums, which is the widening D109 §4
    // declines. MEASURED: renaming `marketplace.mode` away in the bundle reddens exactly this.
    const missing = sitesOf('assembled prefix')
      .filter(site => !isSubtree(site.key))
      .map(site => `${site.file}: ${site.key}.*`);
    expect(missing, 'a key is assembled from a prefix that names no subtree').toEqual([]);
  });

  it('sees no key at all in a key assembled at run time — the limit, where this file says it is', () => {
    // THE LIMIT, DRIVEN THROUGH THE SHIPPED EXTRACTORS rather than through transcriptions of them —
    // a copy of a pattern is a thing that can agree with the prose while the real one has moved. The
    // markup below is written to a scratch file and handed to every shape in `SHAPES`, so what is
    // asserted is what the four walks above actually do.
    //
    // Three shapes a reader might assume are covered and which are not: a key assembled from a
    // prefix, a key handed in by a parent, and a key held in a variable. The whole file is a claim
    // about LITERALS, and a reader who believes otherwise would trust it exactly where it is blind.
    const computed = [
      `<p [abmTranslate]="'marketplace.state.failed.' + whichever()">x</p>`,
      `<p [abmTranslate]="keyFromAParent()">x</p>`,
      `{{ someVariableHoldingAKey | translate }}`,
      `<p [abmTranslate]="'marketplace.state.failed' + '.NO_SUCH_KEY'">x</p>`,
    ].join('\n');
    const scratch = path.join(mkdtempSync(path.join(tmpdir(), 'new91-limit-')), 'computed.html');
    writeFileSync(scratch, computed);
    // EVERY shape except the prefix one, redirected at that file, must see nothing at all. Derived from
    // `SHAPES` itself, so a shape added without thinking about the limit is covered the day it exists.
    const exercised = Object.keys(SHAPES).filter(shape => shape !== 'assembled prefix');
    expect(exercised.length, 'SHAPES went empty — the assertion below would then be vacuous').toBe(Object.keys(SHAPES).length - 1);
    const seen = exercised.flatMap(shape => sitesOfShape(shape, [scratch]).map(site => `${shape}: ${site.key}`));
    expect(seen, 'an extractor read a key out of a run-time expression — the stated limit is wrong').toEqual([]);
    // And the PARTIAL that does reach two of the four: the prefix, and nothing past it. Note the fourth
    // line of markup — `'a.b' + '.c'` — is seen as `a.b` too, so a key split across a concatenation of
    // two literals is covered only as far as its first half. That is the honest reading of "literal".
    const prefixes = sitesOfShape('assembled prefix', [scratch]).map(site => site.key);
    expect(prefixes, 'the prefix arm must still see the prefix').toEqual(['marketplace.state.failed']);
    expect(resolves('marketplace.state.failed'), 'and a prefix is not a sentence').toBe(false);
    expect(isSubtree('marketplace.state.failed'), 'only that the family is there').toBe(true);
    rmSync(path.dirname(scratch), { recursive: true, force: true });
  });
});
