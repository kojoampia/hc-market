import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { TranslateDirective } from 'app/shared/language';

/**
 * What a screen shows when there is nothing to show — the state the prototype does not have.
 *
 * <p>Every prototype screen reads an array already in memory, so "the catalogue could not be
 * fetched" is a case its markup has never had to express. This component is the three answers, in
 * one place so three screens cannot answer differently:
 *
 * <ul>
 *   <li><b>loading</b> — a labelled, bounded statement that a request is in flight. Not a bare
 *       spinner: a spinner says "wait" for ever and says it identically to a failure.</li>
 *   <li><b>failed</b> — a named failure with a <b>retry</b>, because a public read that fails is
 *       usually the estate being restarted and the visitor's only useful action is to ask again.
 *       It never shows a status code: the estate's `ExceptionTranslator` redacts under `prod` and a
 *       500 on a marketplace page is noise a visitor cannot act on.</li>
 *   <li><b>empty</b> — an ordinary answer, with the screen's own words, because "no professional
 *       matches these filters" and "this marketplace has no professionals" are different sentences
 *       and only the screen knows which it is.</li>
 * </ul>
 *
 * <p><b>ALL THREE renders take the screen's own words now, and `empty` was the only one that did
 * until NEW-60 (D105 §6).</b> That asymmetry was invisible while every caller was a marketplace
 * screen, because the marketplace's defaults — *"The marketplace could not be reached / The
 * catalogue did not answer"* — are the right sentence on all three of them. They are the **wrong**
 * sentence on an activation screen: somebody who clicked a link in a mail message has not asked
 * about a catalogue, and telling them the marketplace is down points them at the wrong thing
 * entirely. Every key keeps its old default, so the three public screens render byte-identically to
 * what D103 measured in a browser.
 *
 * <p><b>This component lives under `app/marketplace/` and is used from `app/account/` too.</b>
 * `CLAUDE.md` names this path as *the* place a screen branches on three states, so the account
 * screens reach for it rather than growing a second copy — a verbatim-copy family nobody diffs is
 * how two screens come to answer a failure differently.
 */
@Component({
  selector: 'abm-load-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './load-state.html',
  imports: [TranslateDirective],
})
export default class LoadState {
  readonly kind = input.required<'loading' | 'failed' | 'empty'>();
  /** The `empty` render's headline key. Required for `empty`, ignored otherwise. */
  readonly emptyTitleKey = input('marketplace.state.empty.title');
  readonly emptyDetailKey = input('marketplace.state.empty.detail');

  readonly loadingTitleKey = input('marketplace.state.loading.title');
  readonly loadingDetailKey = input('marketplace.state.loading.detail');

  readonly failedTitleKey = input('marketplace.state.failed.title');
  readonly failedDetailKey = input('marketplace.state.failed.detail');
  /** The retry button's label. The button itself is always rendered on `failed`. */
  readonly failedRetryKey = input('marketplace.state.failed.retry');

  readonly retry = output();
}
