// Asserts what `ng build` copied into target/classes/static, and what it did not.
//
// WHY THIS EXISTS. JHipster generates `angular.json`'s `assets` with `"src/main/webapp/content"` as
// a BARE STRING, which the builder expands to `{ glob: '**/*', input: …, output: 'content' }` — the
// whole directory, `scss/` included. So every production build publishes `content/scss/*.scss`: the
// BridgeCare token definitions, the `_bootstrap-variables` overrides and every layout decision in
// them, fetchable by anyone who guesses the path. `hc-admin/app` does exactly this on
// admin.abofonsa.com today, and `hc-professional/web` carries an `ignore` that does not ignore Sass.
// decisions.md D90 §3 names it as one of three traps to close at generation; this is the half that
// makes the fix checkable.
//
// THE GLOB IS NOT THE GUARD, AND THAT IS THE WHOLE POINT. hc-admin's item 114 learned it the
// expensive way: a glob that looks right and a directory that ships anyway is this defect's entire
// failure mode, and its count was wrong — eight, because it counted one extension, against NINE,
// because nothing had listed the directory. So neither rule below reads `angular.json`. That would
// be the configuration checked against itself.
//
//   RULE 1 is SASS_ARTEFACT over the whole output tree — a literal pattern, read from nowhere.
//   RULE 2 derives its expected set from the SOURCE directory, so a glob narrowed too far fails
//          against the assets it stopped copying.
//
// RULE 2 IS NOT DECORATION, and it guards the direction RULE 1 cannot see. `ignore: ["scss/**"]` is
// one edit away from `ignore: ["**"]`, `["**/*"]` or a typo that matches more than it reads as:
// every one of those publishes no Sass and satisfies RULE 1 completely, while quietly dropping the
// images and `loading.css`. That failure is silent in the build — `ng build` exits 0 — and shows up
// as an unstyled splash with no icon, in production only.
//
// WHY NOT A VITEST SPEC. `npm test` never produces `target/classes/static` — the Angular unit-test
// builder builds no bundle — so a spec over that directory is green on a stale tree and skips on a
// clean checkout, and a skip is the vacuous pass this file exists to prevent. The guarded thing is
// build OUTPUT, so the check runs after a build. Nor is it an esbuild plugin beside
// `define-esbuild.ts`: assets are copied by the builder AFTER the bundling pass, so a plugin hook is
// the wrong side of the event it observes.
//
// Not covered, deliberately: the hashed bundles. `main-*.js`, `styles-*.css` and the chunks are
// content digests whose names move with every code change, so asserting over them would be a
// byte-identity check wearing an inventory check's clothes.

import { readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUTPUT = path.join(ROOT, 'target', 'classes', 'static');
const SOURCE_CONTENT = path.join(ROOT, 'src', 'main', 'webapp', 'content');

// ONE PATTERN, TWO USERS, AND THEY MUST MOVE TOGETHER. Rules 1 and 2 both ask "is this a Sass
// artefact?", and `angular.json`'s `ignore` glob asks a THIRD question — "is this under `scss/`?" —
// which is a different question with the same answer today, because every Sass file in this tree
// lives in `content/scss/`. That is the drift hazard, and it is mitigated rather than removed:
// because RULE 1 reads the OUTPUT, a `.scss` written anywhere else under `content/` ships and this
// rule catches it, loudly.
//
// `.sass` and `.map` are in the pattern although nothing here produces either today —
// `inlineStyleLanguage` is `scss`, there is no `.sass` file under `src/main/webapp`, and the
// production configuration sets `sourceMap: false`. The hole they close is what a hand-written file
// or a by-hand `sass --source-map` run would fall into. Case-insensitive because hc-vendor's review
// defeated the first cut of their equivalent by planting a copy at `content/scss/PROBE.SCSS`, which
// shipped while the check printed zero and exited 0.
const SASS_ARTEFACT = /\.(scss|sass)(\.map)?$/i;

// A spec is not an asset. Nothing in `content/` is a spec in this tree today — `brand-contrast.spec.ts`
// deliberately sits at the webapp root, OUTSIDE the directory that ships, which is hc-admin's ninth
// published file avoided rather than excepted. The pattern is kept because the `ignore` glob would
// correctly refuse such a file, and without this RULE 2 would then demand the build publish its own
// test. It is narrow on purpose: `*.spec.ts` and nothing wider, because excluding "source files" by
// extension would quietly stop requiring shipped scripts like hc-admin's `loading-error.js`.
//
// A spec that ships anyway is still caught by RULE 2's OTHER half: excluded from `expected` and
// present in the output, it lands in `unexpected`. The pair refuses it in both directions.
const SPEC_SOURCE = /\.spec\.ts$/i;

/** Source files under `content/` that are deliberately not published. */
const notAnAsset = file => SASS_ARTEFACT.test(file) || SPEC_SOURCE.test(file);

/** Every file under `dir`, as paths relative to it, POSIX-separated, sorted. */
const filesUnder = dir => {
  const walk = (current, prefix) =>
    readdirSync(current, { withFileTypes: true }).flatMap(entry => {
      const relative = prefix ? `${prefix}/${entry.name}` : entry.name;
      return entry.isDirectory() ? walk(path.join(current, entry.name), relative) : [relative];
    });
  return walk(dir, '').sort();
};

const exists = relative => {
  try {
    return statSync(path.join(OUTPUT, relative)).isFile();
  } catch {
    return false;
  }
};

const failures = [];
const fail = (rule, message) => failures.push(`${rule}: ${message}`);

// FAIL-CLOSED, and this is the line that stops the whole check passing vacuously. A guard over a
// directory that is not there has nothing to report and would otherwise exit 0 — which is precisely
// the shape of the defect being fixed, one level up.
if (!exists('index.html')) {
  console.error(`No build output at ${path.relative(ROOT, OUTPUT)} — run \`npm run webapp:prod\` first.`);
  process.exit(1);
}

const built = filesUnder(OUTPUT);

// RULE 1 — no Sass source is published, over the WHOLE output tree rather than over `content/scss/`
// alone, so a second asset entry copying sources somewhere else fails too.
const published = built.filter(file => SASS_ARTEFACT.test(file));
if (published.length > 0) {
  fail('RULE 1', `${published.length} Sass artefact(s) published to the browser: ${published.join(', ')}`);
}

// RULE 2 — everything in `src/main/webapp/content` that is not a Sass artefact or a spec is
// published, and nothing under `content/` is published that has no source counterpart. The Sass half
// of the exclusion is the SAME pattern RULE 1 uses: narrow it alone and a variant the glob correctly
// refuses becomes a file this rule demands, so the pair cannot drift quietly.
const expected = filesUnder(SOURCE_CONTENT)
  .filter(file => !notAnAsset(file))
  .map(file => `content/${file}`);
const actual = built.filter(file => file.startsWith('content/'));

const missing = expected.filter(file => !actual.includes(file));
if (missing.length > 0) {
  fail('RULE 2', `${missing.length} source asset(s) not copied: ${missing.join(', ')}`);
}
const unexpected = actual.filter(file => !expected.includes(file));
if (unexpected.length > 0) {
  fail('RULE 2', `${unexpected.length} file(s) under content/ with no source counterpart: ${unexpected.join(', ')}`);
}

// The summary names the PATTERN rather than the word "Sass", so the line cannot claim more than the
// rule checks.
if (failures.length > 0) {
  console.error(`check-built-assets: FAILED over ${built.length} built file(s)\n  ${failures.join('\n  ')}`);
  process.exit(1);
}

console.log(
  `check-built-assets: ${built.length} built file(s), ` + `0 matching ${SASS_ARTEFACT}, ` + `${expected.length} content asset(s) published`,
);
