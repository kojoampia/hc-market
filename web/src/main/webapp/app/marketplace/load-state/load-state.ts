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

  readonly retry = output();
}
