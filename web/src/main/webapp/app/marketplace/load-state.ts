import { Signal, WritableSignal, signal } from '@angular/core';

import { Observable } from 'rxjs';

/**
 * Three states, and the third one is the whole reason this exists.
 *
 * <p>The prototype has no unreachable-estate state at all — every one of its screens reads an array
 * that is already in memory, so "the catalogue could not be fetched" is a case its markup has never
 * had to express. A real client must, and <b>"a spinner for ever" is a decision nobody took</b>:
 * it is what a template written as `@if (loaded) { … } @else { spinner }` does on every failure,
 * and it is indistinguishable from a slow network for exactly as long as somebody is willing to
 * wait.
 *
 * <p>So the states are named and every screen branches on all three. `failed` is a distinct render
 * with a retry, not a blank; and `ready` carrying nothing is <b>not</b> an error — an empty
 * catalogue and an empty filter result are ordinary answers, which is why emptiness is asked of the
 * data rather than folded in here.
 *
 * <p>There is deliberately no `error` payload. A public read that fails names nothing a visitor can
 * act on — the estate's own `ExceptionTranslator` redacts under `prod`, and a status code on a
 * marketplace page is noise — so the message is the screen's, in a translation key, and the detail
 * goes to the console through the generated error interceptor.
 */
export type LoadStatus = 'loading' | 'ready' | 'failed';

export interface Loadable<T> {
  status: LoadStatus;
  value: T | null;
}

export const loading = <T>(): Loadable<T> => ({ status: 'loading', value: null });

/**
 * Subscribes and drives a signal through the three states.
 *
 * <p>The signal is RESET to `loading` on every call, which matters on Browse: a filter change
 * re-issues the query, and leaving the previous page on screen with no indication would show a
 * result set that does not match the controls beside it.
 *
 * <p>Returns the signal it was given so a caller can write `readonly cards = drive(signal, …)`.
 */
export const drive = <T>(target: WritableSignal<Loadable<T>>, source: Observable<T>): Signal<Loadable<T>> => {
  target.set(loading<T>());
  source.subscribe({
    next: value => target.set({ status: 'ready', value }),
    error: () => target.set({ status: 'failed', value: null }),
  });
  return target.asReadonly();
};

export const loadableSignal = <T>(): WritableSignal<Loadable<T>> => signal<Loadable<T>>(loading<T>());
