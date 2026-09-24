import { Directive, TemplateRef, ViewContainerRef, computed, effect, inject, input } from '@angular/core';

import { AccountService } from 'app/core/auth/account.service';

/**
 * @whatItDoes Conditionally includes an HTML element if current user has any
 * of the authorities passed as the `expression`.
 *
 * @howToUse
 * ```
 *     <some-element *abmHasAnyAuthority="'ROLE_ADMIN'">...</some-element>
 *
 *     <some-element *abmHasAnyAuthority="['ROLE_ADMIN', 'ROLE_USER']">...</some-element>
 * ```
 */
@Directive({
  selector: '[abmHasAnyAuthority]',
})
export default class HasAnyAuthorityDirective {
  readonly authorities = input<string | string[]>([], { alias: 'abmHasAnyAuthority' });

  private readonly templateRef = inject(TemplateRef<any>);
  private readonly viewContainerRef = inject(ViewContainerRef);

  constructor() {
    const accountService = inject(AccountService);
    const currentAccount = accountService.account;
    const hasPermission = computed(() => currentAccount()?.authorities && accountService.hasAnyAuthority(this.authorities()));

    effect(() => {
      if (hasPermission()) {
        this.viewContainerRef.createEmbeddedView(this.templateRef);
      } else {
        this.viewContainerRef.clear();
      }
    });
  }
}
