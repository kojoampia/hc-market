#!/usr/bin/env node
/* ==========================================================================
 *  Does the prototype's LIVE MODE still agree with the API? — decisions.md D29
 *
 *  Usage:  node deploy/verify-prototype-live.mjs [gatewayBase]
 *          node deploy/verify-prototype-live.mjs http://127.0.0.1:15509
 *
 *  Needs a running estate. Read-only: it fetches public endpoints and nothing else.
 *
 *  --- WHY THIS EXISTS ------------------------------------------------------
 *
 *  The prototype now maps API responses onto its own shapes, and every one of those field names is
 *  a guess until something checks it. The first version of the mapping used `customerName`,
 *  `postedOn` and `reply` for reviews; the API calls them `authorName`, `publishedOn` and
 *  `professionalReply`. Nothing threw. The screen rendered review cards with a blank byline and no
 *  date, which looks like sparse demo data rather than like a bug, and it would have shipped.
 *
 *  A renamed field on either side is exactly that class of failure: silent, plausible, and only
 *  visible to somebody who already knows what the value should have been.
 *
 *  --- IT RUNS THE SHIPPED CODE, NOT A COPY OF IT ---------------------------
 *
 *  The mapping is extracted from the prototype's LAST script block and executed here against a
 *  stubbed browser. Re-implementing it in this file would have been simpler and worthless: the copy
 *  would pass while the page drifted. Same discipline as extract-seed.mjs, which evaluates the
 *  FIRST script block rather than restating its data.
 *
 *  The two blocks are read separately and that separation is load-bearing — see the comment at the
 *  top of the live block for why the seed extractor must never see a network call.
 * ========================================================================== */
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PROTOTYPE = path.resolve(HERE, '../docs/Abofonsa_BridgeCare_Marketplace.html');
const BASE = process.argv[2] || 'http://127.0.0.1:15509';

const html = fs.readFileSync(PROTOTYPE, 'utf8');

/** The demo's data and helpers: the first script block, exactly as extract-seed.mjs reads it. */
function firstBlock() {
  const open = html.indexOf('<script>');
  const close = html.indexOf('</script>', open);
  if (open < 0 || close < 0) throw new Error('no script block found in the prototype');
  return html.slice(open + '<script>'.length, close);
}

/** The live-mode block: the last one in the file. */
function lastBlock() {
  const open = html.lastIndexOf('<script>');
  const close = html.indexOf('</script>', open);
  const source = html.slice(open + '<script>'.length, close);
  if (!source.includes('LIVE MODE')) throw new Error('the last script block is not the live-mode block');
  return source;
}

/* A browser, to the extent this block touches one. Everything stubbed here is something the block
   is allowed to use; anything it reaches for that is missing will throw, which is the point. */
let rendered = 0;
const banner = { textContent: '', style: {} };
const sandbox = {
  console,
  fetch,
  URLSearchParams,
  TextDecoder,
  Promise,
  setTimeout,
  location: { search: `?api=${BASE}`, origin: BASE },
  document: {
    querySelector: () => null,
    querySelectorAll: () => [],
    getElementById: id => (id === 'liveBanner' ? banner : null),
    createElement: () => banner,
    body: { appendChild: () => {} },
  },
  render: () => {
    rendered += 1;
  },
  toast: () => {},
};
sandbox.globalThis = sandbox;

const context = vm.createContext(sandbox);
vm.runInContext(firstBlock(), context);

/* The demo's own figures, captured BEFORE the live load overwrites them. If the two agree
   afterwards, the estate is serving what the prototype was built from — which is the whole claim
   the seed makes. */
const demo = vm.runInContext(
  `({ pros: PROS.length, reviews: REVIEWS.length,
      p1Rating: PROS.find(p=>p.id==='p1').rating,
      p1Reviews: PROS.find(p=>p.id==='p1').reviewCount,
      services: PROS.reduce((n,p)=>n+p.services.length,0) })`,
  context
);

vm.runInContext(lastBlock(), context);

/* The block's own load() is fired by an IIFE and is not returned, so wait for it to settle rather
   than reaching into it. render() being called is the signal that it finished. */
const deadline = Date.now() + 60_000;
while (rendered === 0 && Date.now() < deadline) {
  await new Promise(resolve => setTimeout(resolve, 200));
}

const live = vm.runInContext(
  `({ pros: PROS.length, reviews: REVIEWS.length,
      p1: PROS.find(p=>p.id==='p1'),
      services: PROS.reduce((n,p)=>n+p.services.length,0),
      avail: (AVAIL['p1']||[]).length,
      review: REVIEWS[0] })`,
  context
);

const p1 = live.p1 || {};
const review = live.review || {};
const checks = [
  ['live mode ran', rendered > 0, true],
  ['banner says LIVE', /LIVE|Live/.test(banner.textContent), true],
  ['professionals', live.pros, demo.pros],
  ['reviews', live.reviews, demo.reviews],
  ['services', live.services, demo.services],
  ['p1 rating equals the demo’s', p1.rating, demo.p1Rating],
  ['p1 review count', p1.reviewCount, demo.p1Reviews],
  ['p1 category is lower-cased', p1.cat, 'nutrition'],
  ['p1 modes are prose', JSON.stringify(p1.modes), JSON.stringify(['In person', 'Online', 'Home visit'])],
  ['p1 verified is a boolean', p1.verified, true],
  ['p1 bio present', (p1.bio || '').length > 0, true],
  ['p1 credentials present', (p1.creds || []).length > 0, true],
  ['prices are cedis, not pesewas', p1.services?.[0]?.price, 280],
  ['availability days present', live.avail > 0, true],
  ['review author', !!review.author, true],
  ['review initials', !!review.ini, true],
  ['review date', !!review.date, true],
  ['review body', !!review.text, true],
  ['some review carries a reply', vm.runInContext('REVIEWS.some(r=>r.reply)', context), true],
];

let failed = 0;
for (const [name, got, want] of checks) {
  const ok = String(got) === String(want);
  if (!ok) failed += 1;
  console.log(`  ${ok ? 'ok  ' : 'FAIL'} ${name.padEnd(32)} ${got}${ok ? '' : `   want ${want}`}`);
}
console.log(failed ? `\n${failed} check(s) failed against ${BASE}` : `\nprototype live mode agrees with ${BASE}`);
process.exit(failed ? 1 : 0);
