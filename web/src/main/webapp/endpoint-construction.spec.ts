import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

import { MICROSERVICE } from './app/config/microservices';

/**
 * Every endpoint URL is built through `ApplicationConfigService.getEndpointFor(api, microservice?)`.
 *
 * <p>This is the house pattern across `hc-admin`, `hc-patient` and `hc-professional`, and the rule
 * behind it is stated in the workspace guide: <i>a frontend never writes a service path literally</i>.
 * In hc-market it is sharper than style. The gateway routes `/services/&lt;service&gt;/api/**` and
 * nothing wider, and `hc-market/CLAUDE.md` calls that narrowness a security control — so a
 * hand-written `/services/…` is a path whose correctness depends on a route predicate the client
 * cannot see, in an estate where Stage D fans ONE screen family out to THREE upstreams behind one
 * `/api/pro` prefix.
 *
 * <p>Stage A ships no such call. This exists so that Stage B onwards cannot introduce one quietly:
 * the generated tree passes it today, so the first failure it reports will be a new literal.
 *
 * <p><b>What this reaches, and what it does not.</b> It matches source text, so comments are stripped
 * first — a check whose reach depends on prose is not a check, and this repository has found that
 * eight times. It cannot see a URL assembled at run time from pieces (`'/services/' + name`), and it
 * does not try: that is a shape no reviewer would miss, whereas a plausible literal in a new service
 * file is exactly what slips through. The positive control below is what stops the whole file
 * passing because a glob matched nothing.
 */
describe('endpoint construction', () => {
  const APP = 'src/main/webapp/app';

  /** Every `.ts` under `app/`, excluding specs. */
  const sources = ((): string[] => {
    const walk = (dir: string): string[] =>
      readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) return walk(full);
        return entry.name.endsWith('.ts') && !entry.name.endsWith('.spec.ts') ? [full] : [];
      });
    return walk(APP).sort();
  })();

  /**
   * Line comments and block comments blanked, line numbering preserved so a finding can quote the
   * original line, because a finding quotes the line back by number.
   *
   * <p><b>⚠ IT TRACKS STRING LITERALS, AND THE VERSION THAT DID NOT WAS FAIL-OPEN.</b> This javadoc
   * used to say the opposite, in the same words `.github/checks/strip-comments.awk` used to carry:
   * <i>"String literals are NOT tracked … which fails CLOSED — it can only hide code from a ban,
   * never invent a match — and this tree has no such literal."</i> <b>Hiding code from a ban IS
   * fail-open</b>, which is D77's finding on the Java stripper, measured and stated there in as many
   * words: for a check that must NOT find something, text that is not there cannot be matched.
   *
   * <p>Measured here on 2026-09-24, one probe file under `app/`, with the same-file-minus-the-string
   * control that proves the walk reaches it:
   *
   * <pre>
   *   readonly hint = 'a /* b';                                     <- unterminated opener in a STRING
   *   this.http.get&lt;unknown&gt;('/services/healthconnectcatalog/…')     <- a literal gateway service path
   *   =&gt; 7 passed. Invisible to ALL THREE bans.
   *   delete only the `hint` line =&gt; 3 failed, each naming it.
   * </pre>
   *
   * <p>The milder form lands too: a `//` inside a string truncated the rest of that line.
   *
   * <p><b>Today's exposure was nil and that is stated rather than implied.</b> Method, which travels
   * with the figure: run both strippers over every file and compare output <b>line by line</b> — both
   * emit one line per input line, so the outputs align by number, and `diff` must not be used because
   * it realigns and over-counts (D77 records the same). Measured over the <b>97 non-spec files under
   * `app/`</b>: <b>2 files differ, over 2 lines, 0 truncated to end of file</b> —
   * `shared/jhipster/error.constants.ts:1` and `shared/jhipster/problem-details.ts:18`, both cut by the
   * old version at the `//` in `'https://www.jhipster.tech/…'`. Nothing was truncated to EOF because no
   * string in this tree carries an unterminated `/*`. <b>The claim was the defect, not the leak.</b>
   *
   * <p>A review of this change reported 3 files and one whitespace-only difference; re-measured here it
   * is 2 and none. That gap is a classification difference rather than a disagreement about the tree,
   * and it is left standing rather than quietly reconciled — <b>quote the method with the number, and
   * re-derive rather than copy</b>, which is the rule this paragraph exists to demonstrate.
   *
   * <p><b>Four states, and which of them cross a line is the safety property</b>, copied from
   * `strip-comments.awk`: `inBlock` and a TEMPLATE literal carry, because both genuinely span lines in
   * TypeScript; single- and double-quoted strings are <b>reset at end of line</b>, so a construct this
   * does not understand costs one line rather than the rest of the file. A backslash escapes the next
   * character in all three.
   *
   * <p>Not handled, stated rather than assumed: a <b>regex literal</b> is not tracked, so an unescaped
   * `//` or `/*` inside one (`/[/]/`) would be read as a comment — there is none in this tree, the
   * escaped forms a regex actually needs are unaffected, and the failure is one line. The estate's own
   * `strip-comments.awk` is a <b>Java</b> stripper: it knows text blocks and char literals and not
   * template literals, so it is the wrong language for this file and is deliberately not called.
   */
  const strip = (source: string): string[] => {
    let inBlock = false;
    let inTemplate = false;
    return source.split('\n').map(line => {
      let out = '';
      let inSingle = false;
      let inDouble = false;
      for (let i = 0; i < line.length; i++) {
        const c = line[i];
        if (inBlock) {
          if (line.startsWith('*/', i)) {
            inBlock = false;
            i++;
          }
          out += ' ';
          continue;
        }
        if (inTemplate) {
          if (c === '\\') {
            out += line.slice(i, i + 2);
            i++;
            continue;
          }
          out += c;
          if (c === '`') inTemplate = false;
          continue;
        }
        if (inSingle || inDouble) {
          if (c === '\\') {
            out += line.slice(i, i + 2);
            i++;
            continue;
          }
          out += c;
          if (inSingle && c === "'") inSingle = false;
          else if (inDouble && c === '"') inDouble = false;
          continue;
        }
        if (line.startsWith('/*', i)) {
          inBlock = true;
          i++;
          out += '  ';
          continue;
        }
        if (line.startsWith('//', i)) break;
        if (c === '`') {
          out += c;
          inTemplate = true;
          continue;
        }
        if (c === "'") {
          out += c;
          inSingle = true;
          continue;
        }
        if (c === '"') {
          out += c;
          inDouble = true;
          continue;
        }
        out += c;
      }
      // inSingle/inDouble go out of scope here deliberately: a normal string may not span a line, so
      // an unterminated one costs this line and not the rest of the file. inBlock and inTemplate carry.
      return out;
    });
  };

  const stripped = new Map(sources.map(file => [file, strip(readFileSync(file, 'utf8'))]));

  it('has source files to read', () => {
    // The control. Every assertion below is satisfied by a walk that found nothing.
    expect(sources.length).toBeGreaterThan(30);
    expect(sources).toContain(path.join(APP, 'core/config/application-config.service.ts'));
    const joined = [...stripped.values()].flat().join('\n');
    expect(joined).toContain('getEndpointFor');
  });

  it('strips comments rather than matching them', () => {
    // Without this the ban below would be satisfied by its own explanatory prose, and the file that
    // documents `/services/healthconnectcatalog/api/...` would be the file that fails.
    const probe = strip(["const a = 1; // '/services/probe/api/x'", "/* '/services/probe/api/y' */ const b = 2;"].join('\n'));
    expect(probe.join('\n')).not.toContain('/services/');
    expect(probe[0]).toContain('const a = 1;');
    expect(probe[1]).toContain('const b = 2;');
  });

  it('leaves real code alone', () => {
    // THE CONTROL FOR EVERY OTHER ASSERTION ABOUT THE STRIPPER — `strip-comments-test.sh` needed this
    // one too, and for the same reason: a stripper that returned empty lines would satisfy every
    // "does not contain" expectation in this file, and the three bans would then pass over a tree
    // they had not read.
    const code = ["const url = '/services/healthconnectcatalog/api/professionals';", 'const tpl = `a ${x} b`;', "const re = 'a\\\\'b';"];
    const kept = strip(code.join('\n'));
    expect(kept).toEqual(code);
  });

  it('does not read a comment opener inside a string literal', () => {
    // D77's finding, in TypeScript. The old version treated `/*` in a string as opening a block
    // comment that never closed, so everything below it vanished — and for a check that must NOT
    // find something, text that is not there cannot be matched. That is fail-OPEN.
    const probe = strip(["const hint = 'a /* b';", "this.http.get('/services/healthconnectcatalog/api/professionals');"].join('\n'));
    expect(probe[0]).toContain('/* b');
    expect(probe[1], 'the line after an unterminated /* inside a string must survive').toContain('/services/healthconnectcatalog');

    // The version it replaced, reproduced rather than described: block state only, no string
    // tracking. It must still lose the second line, or this case is distinguishing nothing.
    const stringBlind = (source: string): string[] => {
      let inBlock = false;
      return source.split('\n').map(line => {
        let out = '';
        for (let i = 0; i < line.length; i++) {
          if (inBlock) {
            if (line.startsWith('*/', i)) {
              inBlock = false;
              i++;
            }
            out += ' ';
            continue;
          }
          if (line.startsWith('/*', i)) {
            inBlock = true;
            i++;
            out += '  ';
            continue;
          }
          if (line.startsWith('//', i)) break;
          out += line[i];
        }
        return out;
      });
    };
    const blind = stringBlind(["const hint = 'a /* b';", "this.http.get('/services/healthconnectcatalog/api/professionals');"].join('\n'));
    expect(blind[1].trim(), 'the old stripper must still be blind, or this case proves nothing').toBe('');
  });

  it('does not read a line-comment opener inside a string literal', () => {
    // The milder half of the same defect: `//` in a string truncated the rest of the line, so a
    // literal written after a URL on one line was invisible.
    const probe = strip(["const u = 'http://x'; this.http.get('/services/healthconnectcatalog/api/z');"].join('\n'));
    expect(probe[0]).toContain('/services/healthconnectcatalog');
    expect(probe[0]).toContain("'http://x'");
  });

  it('resets an unterminated ordinary string at end of line, and carries a template', () => {
    // The safety property, copied from strip-comments.awk: what cannot span a line is reset, so an
    // unrecognised construct costs one line rather than the rest of the file. A template literal CAN
    // span lines, so it has to carry — and Angular's inline `template:` depends on that.
    const reset = strip(["const broken = 'unterminated", "this.http.get('/services/healthconnectcatalog/api/z');"].join('\n'));
    expect(reset[1], 'an unterminated ordinary string must cost ONE line').toContain('/services/healthconnectcatalog');

    const carried = strip(['const t = `line one', '// not a comment, this is inside the template', 'line three`;'].join('\n'));
    expect(carried[1], 'a template literal spans lines, so its contents are not comments').toContain('not a comment');
  });

  it('writes no literal gateway service path', () => {
    const offenders: string[] = [];
    for (const [file, lines] of stripped) {
      if (file.endsWith(path.join('config', 'microservices.ts'))) continue; // the one declaration
      lines.forEach((line, i) => {
        if (line.includes('/services/')) offenders.push(`${file}:${i + 1}: ${line.trim()}`);
      });
    }
    expect(offenders, 'build these through getEndpointFor(api, microservice) instead').toEqual([]);
  });

  it('writes no microservice name outside the one declaration', () => {
    const offenders: string[] = [];
    for (const [file, lines] of stripped) {
      if (file.endsWith(path.join('config', 'microservices.ts'))) continue;
      lines.forEach((line, i) => {
        for (const name of Object.values(MICROSERVICE)) {
          if (line.includes(name)) offenders.push(`${file}:${i + 1} names ${name}: ${line.trim()}`);
        }
      });
    }
    expect(offenders, 'import MICROSERVICE from app/config/microservices instead').toEqual([]);
  });

  /**
   * `this.http.get<T>(` / `.post(` / … — paired with whatever its FIRST ARGUMENT turns out to be.
   *
   * <p><b>Two shapes, and the second one is why this is a function rather than one regex.</b> The
   * first cut required the argument on the same line as the `(`:
   *
   * <pre>  /\.(get|post|…)(?:&lt;[^(]*&gt;)?\(\s*(.+)$/</pre>
   *
   * which is blind to a WRAPPED call — and `printWidth: 140` means prettier PRODUCES that shape from
   * a long line, with no intent to evade:
   *
   * <pre>
   *   inline    return this.http.get&lt;T&gt;('api/zz');           // seen
   *   wrapped   return this.http.get&lt;T&gt;(                      // NOT seen
   *               'api/zz',
   *             );
   * </pre>
   *
   * <p>Measured at review: one probe planted under `app/`, this spec run alone — inline gave 1 failure
   * naming the offender, wrapped gave <b>5 passed</b>. This is D60's finding one language along, and
   * that decision's direction is followed here too: it widened the estate's zone-write check to match
   * `.zoneId(\n  raw)` <i>because prettier formats Java in this repository</i>.
   *
   * <p><b>It fails CLOSED and that is the choice.</b> A legitimately wrapped `getEndpointFor(...)` is
   * not flagged (it is not a string literal), but a wrapped call to anything named `get`/`post`/… whose
   * next line begins with a quote is — including a `Map.get(\n 'key',\n)`. Keep the URL on one line.
   * A literal nobody can see is worse than a red line somebody can move.
   */
  const scanForCalls = (trees: Map<string, string[]>): { file: string; at: number; text: string; firstArg: string }[] => {
    const CALL = /\.(?:get|post|put|patch|delete|head|options)(?:<[^(]*>)?\(\s*(.*)$/;
    const found: { file: string; at: number; text: string; firstArg: string }[] = [];
    for (const [file, lines] of trees) {
      lines.forEach((line, i) => {
        const call = CALL.exec(line);
        if (!call) return;
        let firstArg = call[1].trim();
        let at = i;
        if (firstArg === '') {
          // The `(` ended the line: the first argument is on the next line that is not blank. A line
          // blanked by the comment stripper is skipped for the same reason a comment is.
          let j = i + 1;
          while (j < lines.length && lines[j].trim() === '') j++;
          if (j >= lines.length) return;
          firstArg = lines[j].trim();
          at = j;
        }
        found.push({ file, at, text: lines[at].trim(), firstArg });
      });
    }
    return found;
  };

  /** The same scan over the real tree. Taking the map as a parameter is what makes it testable. */
  const httpCalls = (): { file: string; at: number; text: string; firstArg: string }[] => scanForCalls(stripped);

  it('sees a WRAPPED call and not only an inline one', () => {
    // THE CONTROL FOR THE ALTERNATION ABOVE, and it has to be synthetic because no call in this tree
    // is wrapped today — which is exactly how the blindness survived review the first time. Delete
    // the `firstArg === ''` branch and the second expectation goes red while everything else stays
    // green.
    const inline = new Map([['probe.ts', ["return this.http.get<unknown>('api/zz-probe');"]]]);
    const wrapped = new Map([['probe.ts', ['return this.http.get<unknown>(', "  'api/zz-probe',", ');']]]);

    expect(scanForCalls(inline).map(call => call.firstArg)).toEqual(["'api/zz-probe');"]);
    expect(scanForCalls(wrapped).map(call => call.firstArg)).toEqual(["'api/zz-probe',"]);

    // …and the rule the offender test applies catches the literal in BOTH shapes, at the line the
    // literal is actually on.
    for (const [shape, tree] of [
      ['inline', inline],
      ['wrapped', wrapped],
    ] as const) {
      const literals = scanForCalls(tree).filter(call => /^['"`]/.test(call.firstArg));
      expect(literals, `${shape} literal not caught`).toHaveLength(1);
    }
    expect(scanForCalls(wrapped)[0].at, 'a wrapped offender must be reported at the literal').toBe(1);
    // …and QUOTE that line, not the `(` line above it. Mutating `text: lines[at]` to `text: line`
    // leaves every other assertion here green while the message names the wrong text — diagnostic
    // only, and a diagnostic nobody can act on is how a red build gets re-run instead of read.
    expect(scanForCalls(wrapped)[0].text, 'a wrapped offender must QUOTE the literal').toBe("'api/zz-probe',");
  });

  it('passes every HTTP call a getEndpointFor expression', () => {
    // A bare string or template literal as the URL is the defect; anything else is an expression and
    // is checked by the two bans above plus the reviewer.
    const offenders = httpCalls()
      .filter(call => /^['"`]/.test(call.firstArg))
      .map(call => `${call.file}:${call.at + 1}: ${call.text}`);
    expect(offenders, 'the first argument to an HttpClient call must come from getEndpointFor').toEqual([]);
  });

  /**
   * The count is DERIVED AND PRINTED rather than written into a decision, and that is a correction
   * rather than a flourish.
   *
   * <p>D101 §4 originally recorded "nine call sites, nine `getEndpointFor`". Three people re-derived it
   * and no two agreed — 10 and 11, 9 and 12, 9 and 9 — because every hand-rolled grep of `this.http`
   * on one line has <b>exactly the blindness `scanForCalls` was widened to fix</b>, and misses
   * `auth-jwt.service.ts`, where `.post&lt;JwtToken&gt;(` sits on a line that does not carry `this.http`.
   * The property ("zero literals") is machine-checked above and sound; the number was produced by a
   * method that could not see the shape. So the decision states the property and this line states the
   * numbers.
   *
   * <p><b>"Call sites matched" is NOT "HttpClient calls", and printing it that way is the point.</b>
   * The pattern keys on the method name, so it also matches `TranslateService.get(...)` and anything
   * else called `get`/`post`/…; enumerated on this tree, 14 matches of which 10 are HttpClient. That
   * over-matching is the fail-closed direction and is deliberate — but a number whose population is
   * unstated is how the wrong count reached a decision in the first place, so <b>no relation is
   * asserted between these two figures</b>. They count different things, and an earlier draft of this
   * very test asserted one and went red.
   */
  it('reports what it checked, so no document has to quote a number', () => {
    const calls = httpCalls();
    const CONFIG = path.join(APP, 'core/config/application-config.service.ts');
    const endpointForLines = [...stripped].flatMap(([file, lines]) =>
      lines.flatMap(line => (line.includes('getEndpointFor(') ? [file] : [])),
    );
    // The declaration and its usages are different populations too — the whole subject of this test
    // is that an unstated population is how the wrong count reached a decision.
    const declared = endpointForLines.filter(file => file === CONFIG).length;
    const used = endpointForLines.length - declared;

    // Files that mention HttpClient at all. Derived rather than listed, so it cannot go stale.
    const httpClientFiles = new Set([...stripped].flatMap(([file, lines]) => (lines.some(l => l.includes('HttpClient')) ? [file] : [])));
    const inHttpClientFiles = calls.filter(call => httpClientFiles.has(call.file)).length;

    // eslint-disable-next-line no-console
    console.log(
      `endpoint-construction: ${sources.length} source file(s), ` +
        `${calls.length} call site(s) matched (method-name keyed, so wider than HttpClient; ` +
        `${inHttpClientFiles} in files that import HttpClient), ` +
        `${endpointForLines.length} getEndpointFor(...) line(s) = ${declared} declaration + ${used} usage(s)`,
    );

    // THE CONTROLS. `calls.length > 0` alone is too weak and reads stronger than it is: the four
    // TranslateService/AlertService `.get(` matches satisfy it on their own, so every HttpClient call
    // could leave `app/` with this still green. The HttpClient-scoped count is what actually covers
    // the half this file is about.
    expect(inHttpClientFiles, 'no call site in any file that imports HttpClient — the bans cover nothing').toBeGreaterThan(0);
    expect(declared, 'ApplicationConfigService must still declare getEndpointFor').toBe(1);
    expect(used, 'no caller builds a URL through getEndpointFor').toBeGreaterThan(0);
  });
});
