import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/services/api-client.service';
import { DashboardData } from './dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly api = inject(ApiClient);

  load(): Observable<DashboardData> {
    return this.api.get<DashboardData>('/api/dashboard');
  }
}
