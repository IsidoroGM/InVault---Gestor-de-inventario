import { inject, Injectable, signal } from '@angular/core';
import { catchError, of, timeout } from 'rxjs';

import { ApiClient } from './api-client.service';

@Injectable({ providedIn: 'root' })
export class CompanyProfileService {
  private readonly api = inject(ApiClient);
  readonly name = signal('InVault');

  load(): void {
    this.api
      .get<{ readonly name: string }>('/api/company')
      .pipe(
        timeout(8000),
        catchError(() => of({ name: 'InVault' })),
      )
      .subscribe((profile) => this.name.set(profile.name || 'InVault'));
  }
}
