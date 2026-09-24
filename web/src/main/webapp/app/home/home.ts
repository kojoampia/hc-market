import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language';

@Component({
  selector: 'abm-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.html',
  styleUrl: './home.scss',
  imports: [TranslateDirective],
})
export default class Home {
  public readonly account = inject(AccountService).account;

  private readonly router = inject(Router);

  login(): void {
    this.router.navigate(['/login']);
  }
}
