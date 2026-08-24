/**
 * HealthConnect Marketplace — seed extractor.
 *
 * Regenerates `seed-data.json` from the prototype's in-memory arrays. Per docs/decisions.md D4 the
 * seed is *re-extracted*, never hand-edited, so the fixture cannot drift from the UX contract.
 *
 *   node deploy/demo/extract-seed.mjs
 *
 * It evaluates the prototype's first <script> block in a sandbox and reads the arrays straight out
 * of it. That block is pure data and pure functions — its only DOM references are inside lambdas
 * that are never called at definition time — so a stub `document` is enough.
 *
 * WHAT IS INVENTED, AND BY WHAT RULE
 * ----------------------------------
 * Everything below is invention, because the prototype has no such data. Each rule is applied
 * mechanically and recorded in `$meta.derivationRules` so it is visible in the output rather than
 * buried here.
 *
 *   bookingReference on a review   "b-" + review id        (r1 -> b-r1)
 *   customerLogin                  name -> lowercase, [ \s-]+ -> "."
 *   customer email                 login + "@example.demo", except ME who has a real one
 *
 * The minted booking references do NOT correspond to bookings in the `bookings` section — the
 * prototype's REVIEWS carry no booking id and never did. They exist so `Review.bookingReference`
 * can be non-null and unique. The booking service seeds no matching row; `POST /api/reviews`
 * enforces the real rule at write time against live bookings.
 *
 * WHAT IS NOT EMITTED
 * -------------------
 * Ratings. They are derived from `reviews` at read time by the professional_rating view, so the
 * seed cannot ship a rating that disagrees with its reviews. This is the whole point.
 */

import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PROTOTYPE = path.resolve(HERE, '../../docs/Abofonsa_BridgeCare_Marketplace.html');
const OUT = path.resolve(HERE, 'seed-data.json');

/** The prototype's data block: from the first <script> to the first </script>. */
function readPrototypeData(html) {
  const open = html.indexOf('<script>');
  const close = html.indexOf('</script>', open);
  if (open < 0 || close < 0) throw new Error('no <script> block found in the prototype');
  return html.slice(open + '<script>'.length, close);
}

const EXPORTS = [
  'CATS', 'PROS', 'REVIEWS', 'AVAIL', 'SLOT_TIMES', 'MODES', 'CITIES', 'BOOKINGS', 'THREADS',
  'CLIENTS', 'ME', 'PRO_HISTORY', 'PRO_REQUESTS', 'PRO_SCHEDULE', 'NOTIFICATIONS', 'COMMISSION',
  'PAYOUT_LAG', 'FAVOURITES',
];

function evaluatePrototype(source) {
  const sandbox = vm.createContext({
    document: { querySelector: () => null, querySelectorAll: () => [] },
    console,
  });
  vm.runInContext(`${source}\nglobalThis.__seed = {${EXPORTS.join(',')}};`, sandbox);
  return sandbox.__seed;
}

// ---------------------------------------------------------------- mapping rules --

const DELIVERY_MODE = { 'In person': 'IN_PERSON', Online: 'ONLINE', 'Home visit': 'HOME_VISIT' };
const BOOKING_STATUS = {
  pending: 'REQUESTED',
  requested: 'REQUESTED',
  confirmed: 'CONFIRMED',
  completed: 'COMPLETED',
  cancelled: 'CANCELLED',
  declined: 'DECLINED',
};

const mode = m => DELIVERY_MODE[m] ?? fail(`unmapped delivery mode: ${m}`);
const status = s => BOOKING_STATUS[String(s).toLowerCase()] ?? fail(`unmapped status: ${s}`);
/** The prototype has a boolean. Four states exist so suspension is distinguishable from
 *  never-verified; the seed only ever produces the two the boolean can express. */
const verification = v => (v ? 'VERIFIED' : 'UNVERIFIED');
/** Cedis to pesewas. The prototype holds major units; nothing downstream may. */
const minor = cedis => Math.round(cedis * 100);
const login = name => name.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
  .replace(/[\s-]+/g, '.').replace(/[^a-z.]/g, '');
const initialsOf = n => n.split(' ').filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase();

function fail(message) {
  throw new Error(message);
}

// ------------------------------------------------------------------- transform --

function build(p) {
  const customerByName = new Map();
  const customers = [];
  // ME is c1 and is the only customer the prototype describes in full.
  customers.push({
    ref: 'c1',
    userLogin: login(p.ME.name),
    displayName: p.ME.name,
    initials: p.ME.ini,
    email: p.ME.email,
    phone: p.ME.phone,
    city: p.ME.city,
    addressLine: p.ME.address,
    dateOfBirth: p.ME.dob,
    membership: p.ME.member,
    careSummary: p.ME.notes,
  });
  customerByName.set(p.ME.name, customers[0]);
  /**
   * Everyone else is a name only. The set is the union of every person who acts as a customer
   * anywhere in the prototype — CLIENTS (who attribute the generated history), the clients naming
   * requests, and the review authors. The previous extraction used CLIENTS alone, which silently
   * left 3 request clients and 40 review authors without a row; with `Review.customerLogin` now
   * required (decisions.md D8) that would be a dangling reference.
   */
  const everyCustomerName = [
    ...p.CLIENTS,
    ...p.PRO_REQUESTS.map(r => r.client),
    ...p.PRO_SCHEDULE.map(r => r.client),
    ...p.PRO_HISTORY.map(r => r.client),
    ...p.REVIEWS.map(r => r.author),
  ];
  for (const name of everyCustomerName) {
    if (customerByName.has(name)) continue;
    const row = {
      ref: `c${customers.length + 1}`,
      userLogin: login(name),
      displayName: name,
      initials: initialsOf(name),
      email: `${login(name)}@example.demo`,
      phone: null, city: null, addressLine: null,
      dateOfBirth: null, membership: null, careSummary: null,
    };
    customers.push(row);
    customerByName.set(name, row);
  }
  const customerOf = name => customerByName.get(name) ?? fail(`no customer row for "${name}"`);

  const categories = p.CATS.map((c, i) => ({
    code: c.id.toUpperCase(),
    name: c.name,
    blurb: c.blurb,
    icon: c.icon,
    sortOrder: i + 1,
    specialities: c.subs,
  }));

  const professionals = p.PROS.map(pro => ({
    ref: pro.id,
    userLogin: login(pro.name),
    displayName: pro.name,
    initials: pro.ini,
    headline: pro.title,
    categoryCode: pro.cat.toUpperCase(),
    speciality: pro.sub,
    city: pro.city,
    countryCode: 'GH',
    deliveryModes: pro.modes.map(mode),
    yearsPractising: pro.years,
    verification: verification(pro.verified),
    insured: pro.insured,
    policeClearance: pro.dbs,
    languages: pro.langs,
    responseMinutes: pro.responseMins,
    rebookRatePct: pro.repeat,
    credentials: pro.creds,
    highlights: pro.highlights,
    bio: pro.bio,
    avatarGradientFrom: pro.c[0],
    avatarGradientTo: pro.c[1],
    services: pro.services.map((s, i) => ({
      ref: s.id,
      name: s.name,
      // The prototype's key is `dur`, not `mins`. Getting this wrong is silent: every duration
      // becomes null and the profile screen simply stops showing "60 min" — nothing fails.
      durationMinutes: s.dur,
      priceMinor: minor(s.price),
      currency: 'GHS',
      description: s.desc,
      active: true,
      sortOrder: i + 1,
    })),
    availability: (p.AVAIL[pro.id] ?? []).map(d => ({ date: d.date, slots: d.times })),
  }));

  const reviews = p.REVIEWS.map(r => ({
    ref: r.id,
    professionalRef: r.pro,
    customerLogin: login(r.author),
    authorName: r.author,
    authorInitials: r.ini,
    stars: r.stars,
    publishedOn: r.date,
    body: r.text,
    professionalReply: r.reply ?? null,
    bookingReference: `b-${r.id}`,
  }));

  const serviceIndex = new Map();
  for (const pro of p.PROS) for (const s of pro.services) serviceIndex.set(s.id, { ...s, pro: pro.id });
  const serviceOf = ref => serviceIndex.get(ref) ?? fail(`unknown service ref: ${ref}`);

  const bookings = p.BOOKINGS.map(b => {
    const me = customers[0];
    return {
      ref: b.id,
      customerRef: me.ref,
      customerLogin: me.userLogin,
      customerName: me.displayName,
      professionalRef: b.pro,
      serviceRef: b.svc,
      serviceName: serviceOf(b.svc).name,
      scheduledDate: b.date,
      scheduledTime: b.time,
      deliveryMode: mode(b.mode),
      status: status(b.status),
      priceMinor: minor(b.price),
      currency: 'GHS',
      customerNote: b.note ?? null,
      onBehalfOf: b.who ?? null,
      reviewed: Boolean(b.reviewed),
    };
  });

  const fromClientRow = (r, extra) => {
    const c = customerOf(r.client);
    return {
      ref: r.id,
      customerRef: c.ref,
      customerLogin: c.userLogin,
      customerName: c.displayName,
      professionalRef: 'p1', // the professional workspace is always p1 in the prototype
      serviceRef: r.svc,
      serviceName: serviceOf(r.svc).name,
      deliveryMode: mode(r.mode),
      currency: 'GHS',
      ...extra,
    };
  };

  const requests = p.PRO_REQUESTS.map(r => fromClientRow(r, {
    requestedDate: r.date,
    requestedTime: r.time,
    priceMinor: minor(serviceOf(r.svc).price),
    note: r.note ?? null,
    raisedOn: r.made,
    status: status(r.status),
  }));

  const appointments = p.PRO_SCHEDULE.map(a => fromClientRow(a, {
    scheduledDate: a.date,
    scheduledTime: a.time,
    priceMinor: minor(serviceOf(a.svc).price),
    status: status(a.status),
    note: a.note ?? null,
  }));

  const sessions = p.PRO_HISTORY.map(h => fromClientRow(h, {
    completedDate: h.date,
    startedTime: h.time,
    grossMinor: minor(h.price),
    status: status(h.status),
  }));

  const threads = p.THREADS.map(t => {
    // The prototype's threads are all ME talking to a professional.
    const me = customers[0];
    return {
      ref: t.id,
      customerRef: me.ref,
      customerLogin: me.userLogin,
      professionalRef: t.pro,
      bookingReference: null,
      messages: t.msgs.map((m, i) => ({
        seq: i + 1,
        direction: m.from === 'me' ? 'CUSTOMER_TO_PROFESSIONAL' : 'PROFESSIONAL_TO_CUSTOMER',
        sentAt: `${m.t.replace(' ', 'T')}:00Z`,
        body: m.x,
        read: i < t.msgs.length - t.unread,
      })),
    };
  });

  const notifications = p.NOTIFICATIONS.map(n => ({
    ref: n.id,
    recipientRef: customers[0].ref,
    recipientLogin: customers[0].userLogin,
    kind: n.t,
    body: n.x,
    raisedOn: n.when,
    read: n.read,
  }));

  return {
    $meta: {
      name: 'healthconnect-demo-seed',
      version: '2.0.0',
      generatedFrom: 'Abofonsa_BridgeCare_Marketplace.html',
      generatedBy: 'deploy/demo/extract-seed.mjs',
      demoToday: '2026-08-10',
      loadedByProfiles: ['test', 'dev'],
      note: 'Ratings are NOT stored. They are derived from reviews at read time, exactly as in the prototype.',
      derivationRules: {
        bookingReference: '"b-" + review id; no matching booking row exists — see extract-seed.mjs',
        customerLogin: 'displayName lowercased, runs of spaces and hyphens replaced by "."',
        customerEmail: 'login + "@example.demo", except the one customer the prototype describes in full',
        verification: 'prototype boolean: true -> VERIFIED, false -> UNVERIFIED',
        money: 'prototype major units (cedis) x100 -> minor units (pesewas)',
      },
    },
    brokerage: {
      commissionRate: p.COMMISSION,
      payoutLagDays: p.PAYOUT_LAG,
      currency: 'GHS',
      freeCancellationHours: 24,
      lateCancellationPct: 0.5,
    },
    deliveryModes: p.MODES.map(mode),
    cities: p.CITIES,
    slotTimes: p.SLOT_TIMES,
    categories,
    professionals,
    reviews,
    customers,
    favourites: p.FAVOURITES,
    bookings,
    requests,
    appointments,
    sessions,
    threads,
    notifications,
  };
}

// ----------------------------------------------------------------- self-checks --

/**
 * The extractor asserts the prototype's own figures. A seed that no longer reproduces them is a
 * broken extraction, not a new version — spec §14 checks the same numbers against the live API.
 */
function verify(seed) {
  const grossMinor = seed.sessions.reduce((n, s) => n + s.grossMinor, 0);
  const checks = [
    ['categories', seed.categories.length, 4],
    ['professionals', seed.professionals.length, 18],
    ['services', seed.professionals.reduce((n, p) => n + p.services.length, 0), 52],
    ['reviews', seed.reviews.length, 63],
    ['bookings', seed.bookings.length, 13],
    ['requests', seed.requests.length, 5],
    ['appointments', seed.appointments.length, 12],
    ['sessions', seed.sessions.length, 256],
    ['threads', seed.threads.length, 4],
    ['notifications', seed.notifications.length, 4],
    ['gross minor units', grossMinor, 8162000],
  ];
  const failures = checks.filter(([, got, want]) => got !== want);
  for (const [label, got, want] of checks) {
    console.log(`  ${got === want ? 'ok  ' : 'FAIL'} ${label.padEnd(20)} ${got}${got === want ? '' : ` (expected ${want})`}`);
  }

  /**
   * Required fields must actually be populated. A mistyped source key — `s.mins` where the
   * prototype says `s.dur` — produces null rather than an error, loads cleanly, and shows up only
   * as a missing line on a screen nobody is looking at. Every field named here is one the JDL marks
   * required, or one a prototype screen renders unconditionally.
   */
  const required = {
    professional: [['ref', 'userLogin', 'displayName', 'headline', 'speciality', 'city', 'verification'], seed.professionals],
    service: [['ref', 'name', 'durationMinutes', 'priceMinor', 'currency'], seed.professionals.flatMap(p => p.services)],
    review: [['ref', 'customerLogin', 'authorName', 'stars', 'publishedOn', 'body', 'bookingReference'], seed.reviews],
    category: [['code', 'name', 'sortOrder'], seed.categories],
  };
  for (const [label, [fields, rows]] of Object.entries(required)) {
    const missing = fields.filter(f => rows.some(r => r[f] === null || r[f] === undefined));
    console.log(`  ${missing.length ? 'FAIL' : 'ok  '} ${`${label} fields`.padEnd(20)} ${fields.length} checked over ${rows.length} rows${missing.length ? ` — null: ${missing.join(', ')}` : ''}`);
    if (missing.length) failures.push([`${label} null fields`, missing.join(', '), 'all populated']);
  }

  // No rating may appear anywhere in the file.
  const stray = JSON.stringify(seed).match(/"(rating|reviewCount|totalEarnings)"/);
  if (stray) failures.push(['derived field present in seed', stray[1], 'absent']);

  // bookingReference must be unique, or "one review per booking" is not a schema guarantee.
  const refs = new Set(seed.reviews.map(r => r.bookingReference));
  if (refs.size !== seed.reviews.length) failures.push(['unique bookingReference', refs.size, seed.reviews.length]);

  /**
   * Referential integrity, which matters more than any of the counts above: every customerLogin
   * referenced anywhere must have a row in `customers`. This is the check whose absence let the
   * previous extraction ship 43 dangling references.
   */
  const known = new Set(seed.customers.map(c => c.userLogin));
  const referenced = [
    ...seed.reviews.map(r => r.customerLogin),
    ...seed.bookings.map(b => b.customerLogin),
    ...seed.requests.map(r => r.customerLogin),
    ...seed.appointments.map(a => a.customerLogin),
    ...seed.sessions.map(s => s.customerLogin),
    ...seed.threads.map(t => t.customerLogin),
    ...seed.notifications.map(n => n.recipientLogin),
  ];
  const dangling = [...new Set(referenced.filter(l => !known.has(l)))];
  console.log(`  ${dangling.length ? 'FAIL' : 'ok  '} ${'customer references'.padEnd(20)} ${referenced.length} references, ${seed.customers.length} customers, ${dangling.length} dangling`);
  if (dangling.length) failures.push(['dangling customerLogin', dangling.slice(0, 5).join(', '), 'none']);

  // Every professionalRef and serviceRef must resolve too.
  const pros = new Set(seed.professionals.map(p => p.ref));
  const svcs = new Set(seed.professionals.flatMap(p => p.services.map(s => s.ref)));
  const badPro = [...new Set(seed.reviews.map(r => r.professionalRef).filter(r => !pros.has(r)))];
  const badSvc = [...new Set(seed.sessions.map(s => s.serviceRef).filter(r => !svcs.has(r)))];
  if (badPro.length) failures.push(['unknown professionalRef', badPro.join(', '), 'none']);
  if (badSvc.length) failures.push(['unknown serviceRef', badSvc.join(', '), 'none']);

  if (failures.length) {
    console.error(`\n${failures.length} check(s) failed — not writing the seed.`);
    process.exit(1);
  }
}

// ------------------------------------------------------------------------ main --

const html = fs.readFileSync(PROTOTYPE, 'utf8');
const prototype = evaluatePrototype(readPrototypeData(html));
const seed = build(prototype);

console.log(`Extracted from ${path.relative(process.cwd(), PROTOTYPE)}`);
verify(seed);

fs.writeFileSync(OUT, `${JSON.stringify(seed, null, 2)}\n`);
console.log(`\nWrote ${path.relative(process.cwd(), OUT)} (${(fs.statSync(OUT).size / 1024).toFixed(0)} KB)`);
