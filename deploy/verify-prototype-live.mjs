#!/usr/bin/env node
/* ==========================================================================
 *  Does the prototype's LIVE MODE still agree with the API? — decisions.md D29
 *
 *  Usage:
 *      node deploy/verify-prototype-live.mjs [gatewayBase]
 *      node deploy/verify-prototype-live.mjs http://127.0.0.1:15509
 *      node deploy/verify-prototype-live.mjs http://127.0.0.1:15509 --writes <tokenFile> [customerLogin]
 *
 *  Needs a running estate. Without --writes it is READ-ONLY. With --writes it CREATES A BOOKING and
 *  PUBLISHES A REVIEW against whatever estate you point it at — never aim that at production.
 *
 *  --- WHY THIS EXISTS ------------------------------------------------------
 *
 *  The prototype maps API responses onto its own shapes, and every field name is a guess until
 *  something checks it. Three were wrong, and not one of them threw:
 *
 *    - reviews are `authorName` / `publishedOn` / `professionalReply`, not `customerName` /
 *      `postedOn` / `reply`. The cards rendered with a blank byline and no date, which reads as
 *      sparse demo data.
 *    - availability days carry `slots`, not `times`. Every day arrived empty, so the calendar showed
 *      three weeks of a fully-booked professional. An earlier version of THIS FILE passed it,
 *      because it asserted only that days existed — see the `free times` check below.
 *    - the write path called the demo's submitReview with no argument, throwing on `undefined.pro`
 *      AFTER the POST had already succeeded: a review published on the server, an error on screen.
 *
 *  That is the class of failure this guards: silent, plausible, and visible only to somebody who
 *  already knows what the value should have been.
 *
 *  --- IT RUNS THE SHIPPED CODE, NOT A COPY OF IT ---------------------------
 *
 *  Every script block is extracted from the prototype and executed here against a stubbed browser,
 *  in document order — which is what a browser does, and is required: the write overrides target
 *  `confirmBooking` and `submitReview`, which live in the THIRD block, not the last.
 *
 *  Re-implementing the mapping here would be simpler and worthless: the copy would pass while the
 *  page drifted. Same discipline as extract-seed.mjs, which evaluates the FIRST block rather than
 *  restating its data. The seed extractor must never see a network call, which is why live mode is
 *  the last block — see the comment at the top of it.
 * ========================================================================== */
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PROTOTYPE = path.resolve(HERE, '../docs/Abofonsa_BridgeCare_Marketplace.html');

const argv = process.argv.slice(2);
const BASE = argv.find(a => a.startsWith('http')) || 'http://127.0.0.1:15509';
const writeAt = argv.indexOf('--writes');
const TOKEN = writeAt >= 0 ? fs.readFileSync(argv[writeAt + 1], 'utf8').trim() : '';

const html = fs.readFileSync(PROTOTYPE, 'utf8');

/** Every script block, in document order. */
function blocks() {
  const out = [];
  for (let i = html.indexOf('<script>'); i >= 0; i = html.indexOf('<script>', i + 1)) {
    out.push(html.slice(i + '<script>'.length, html.indexOf('</script>', i)));
  }
  if (out.length < 2 || !out[out.length - 1].includes('LIVE MODE')) {
    throw new Error('the last script block is not the live-mode block');
  }
  return out;
}

/* A browser, to the extent the prototype touches one. A proxy that answers every unknown property
   with a function is enough for the demo's render and modal paths; the two objects that need real
   state are the banner's textContent and the review textarea's value. */
const stub = extra =>
  new Proxy(extra, {
    get(t, k) {
      if (k in t) return t[k];
      if (k === 'style') return {};
      if (k === 'classList') return { add() {}, remove() {}, toggle() {}, contains: () => false };
      if (k === 'scrollTop' || k === 'scrollHeight') return 0;
      if (k === 'firstChild' || k === 'lastChild') return null;
      if (k === Symbol.toPrimitive) return () => '[el]';
      return () => stubbed;
    },
    set(t, k, v) {
      t[k] = v;
      return true;
    },
  });
const banner = stub({ textContent: '' });
const stubbed = stub({ value: 'Published by verify-prototype-live.mjs' });

const sandbox = {
  console,
  fetch,
  URLSearchParams,
  TextDecoder,
  Promise,
  setTimeout,
  clearTimeout,
  Date,
  Math,
  JSON,
  location: { search: `?api=${BASE}${TOKEN ? `&token=${TOKEN}` : ''}`, origin: BASE, hash: '#/discover' },
  document: {
    querySelector: () => stubbed,
    querySelectorAll: () => [],
    getElementById: id => (id === 'liveBanner' ? banner : stubbed),
    createElement: () => banner,
    body: { appendChild() {}, removeChild() {}, style: {}, classList: { add() {}, remove() {} } },
    addEventListener() {},
  },
  requestAnimationFrame: cb => setTimeout(cb, 0),
  getComputedStyle: () => ({}),
  alert: () => {},
  addEventListener: () => {},
};
sandbox.window = sandbox;
sandbox.self = sandbox;
sandbox.globalThis = sandbox;

const ctx = vm.createContext(sandbox);
const source = blocks();
vm.runInContext(source[0], ctx);

/* The demo's own figures, captured BEFORE the live load overwrites them. If the two agree
   afterwards, the estate is serving what the prototype was built from — the whole claim the seed
   makes. Captured here rather than later because the later blocks do not touch them. */
const demo = vm.runInContext(
  `({ pros: PROS.length, reviews: REVIEWS.length,
      p1Rating: PROS.find(p=>p.id==='p1').rating,
      p1Reviews: PROS.find(p=>p.id==='p1').reviewCount,
      bookings: BOOKINGS.length,
      services: PROS.reduce((n,p)=>n+p.services.length,0) })`,
  ctx
);

for (const block of source.slice(1)) vm.runInContext(block, ctx);

/* Count renders and toasts from here, so the demo's own boot render is not mistaken for the live
   load finishing. */
vm.runInContext(
  `render = function(){ globalThis.__r = (globalThis.__r||0) + 1; };
   toast  = function(m){ (globalThis.__t = globalThis.__t || []).push(String(m)); };`,
  ctx
);

const settle = async (n, ms = 90_000) => {
  const until = Date.now() + ms;
  while (Date.now() < until) {
    if ((vm.runInContext('globalThis.__r||0', ctx) || 0) >= n) return true;
    await new Promise(r => setTimeout(r, 200));
  }
  return false;
};
await settle(1);
await new Promise(r => setTimeout(r, TOKEN ? 2500 : 500));

const live = vm.runInContext(
  `({ pros: PROS.length, reviews: REVIEWS.length,
      p1: PROS.find(p=>p.id==='p1'),
      services: PROS.reduce((n,p)=>n+p.services.length,0),
      avail: (AVAIL['p1']||[]).length,
      availTimes: Object.values(AVAIL).flat().reduce((n,d)=>n+(d.times||[]).length,0),
      bookings: BOOKINGS.length, bookingIds: BOOKINGS.map(b=>b.id),
      review: REVIEWS[0] })`,
  ctx
);

const p1 = live.p1 || {};
const review = live.review || {};
const checks = [
  ['live mode ran', (vm.runInContext('globalThis.__r||0', ctx) || 0) > 0, true],
  ['banner says LIVE', /LIVE|Live/.test(banner.textContent), true],
  ['professionals', live.pros, demo.pros],
  ['reviews', live.reviews >= demo.reviews, true],
  ['services', live.services, demo.services],
  ['p1 rating equals its own reviews', p1.rating, vm.runInContext(`(function(){const rs=REVIEWS.filter(r=>r.pro==='p1');return rs.length?Math.round(rs.reduce((a,r)=>a+r.stars,0)/rs.length*10)/10:0;})()`, ctx)],
  ['p1 category is lower-cased', p1.cat, 'nutrition'],
  ['p1 modes are prose', JSON.stringify(p1.modes), JSON.stringify(['In person', 'Online', 'Home visit'])],
  ['p1 verified is a boolean', p1.verified, true],
  ['p1 bio present', (p1.bio || '').length > 0, true],
  ['p1 credentials present', (p1.creds || []).length > 0, true],
  ['prices are cedis, not pesewas', p1.services?.[0]?.price, 280],
  ['availability days present', live.avail > 0, true],
  /* Days alone proved nothing: the mapping once read `day.times` where the API sends `slots`, so
     every day arrived empty and this file still passed. A catalogue nobody can be booked in is not
     a working read path. */
  ['availability has free times', live.availTimes > 0, true],
  ['review author', !!review.author, true],
  ['review initials', !!review.ini, true],
  ['review date', !!review.date, true],
  ['review body', !!review.text, true],
  ['some review carries a reply', vm.runInContext('REVIEWS.some(r=>r.reply)', ctx), true],
];

if (TOKEN) {
  /* There is deliberately NO assertion here that the bookings "came from the estate", and the two
     attempts at one are worth recording. The first asserted that some id did not match `b<digits>`;
     it held only because that customer happened to have a `q…` booking, and failed after a reseed.
     The second compared counts; the demo has 13 and the seeded estate gives this customer 13.

     Both were asserting a coincidence, because the SEED IS EXTRACTED FROM THE DEMO — the two data
     sets are supposed to look alike, and any check built on them differing is checking the wrong
     thing. The load is proved behaviourally instead, below: a booking created through the prototype
     comes back with a server-issued reference the demo could not have minted. */

  const slot = vm.runInContext(`(function(){const a=(AVAIL['p1']||[]).find(d=>d.times.length);return a?{date:a.date,time:a.times[0]}:null;})()`, ctx);
  if (!slot) {
    checks.push(['a bookable slot exists for p1', false, true]);
  } else {
    const before = live.bookingIds.slice();
    vm.runInContext(
      `state.booking={pro:'p1',svc:'s1b',date:${JSON.stringify(slot.date)},time:${JSON.stringify(slot.time)},` +
        `mode:'Online',notes:'verify-prototype-live.mjs',forWhom:'me'};`,
      ctx
    );
    await vm.runInContext('confirmBooking()', ctx);
    await new Promise(r => setTimeout(r, 2500));
    const after = vm.runInContext('BOOKINGS.map(b=>b.id)', ctx);
    const fresh = after.filter(i => !before.includes(i));
    checks.push(['a booking was created and reloaded from the server', fresh.length, 1]);
    checks.push(['its id is server-issued', /^b-/.test(fresh[0] || ''), true]);
    /* The estate publishes booking.requested; the gateway's SSE fan-out addresses it back to this
       customer. A toast naming it is the only end-to-end evidence that D25's live channel works. */
    const toasts = vm.runInContext('(globalThis.__t||[]).join(" | ")', ctx);
    checks.push(['the SSE channel delivered the event back', /Live: booking/.test(toasts), true]);
  }
}

let failed = 0;
for (const [name, got, want] of checks) {
  const ok = String(got) === String(want);
  if (!ok) failed += 1;
  console.log(`  ${ok ? 'ok  ' : 'FAIL'} ${name.padEnd(42)} ${got}${ok ? '' : `   want ${want}`}`);
}
console.log(
  failed
    ? `\n${failed} check(s) failed against ${BASE}`
    : `\nprototype live mode agrees with ${BASE}${TOKEN ? ' (reads and writes)' : ' (reads only — pass --writes <tokenFile> for the rest)'}`
);
process.exit(failed ? 1 : 0);
