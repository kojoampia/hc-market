import { Directive, ElementRef, OnInit, Renderer2, inject, input } from '@angular/core';

import { LangChangeEvent, TranslateService } from '@ngx-translate/core';

@Directive({
  selector: '[abmActiveMenu]',
})
export default class ActiveMenuDirective implements OnInit {
  readonly abmActiveMenu = input();

  private readonly el = inject(ElementRef);
  private readonly renderer = inject(Renderer2);
  private readonly translateService = inject(TranslateService);

  ngOnInit(): void {
    this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
      this.updateActiveFlag(event.lang);
    });

    this.updateActiveFlag(this.translateService.getCurrentLang());
  }

  updateActiveFlag(selectedLanguage: string | null): void {
    if (this.abmActiveMenu() === selectedLanguage) {
      this.renderer.addClass(this.el.nativeElement, 'active');
    } else {
      this.renderer.removeClass(this.el.nativeElement, 'active');
    }
  }
}
