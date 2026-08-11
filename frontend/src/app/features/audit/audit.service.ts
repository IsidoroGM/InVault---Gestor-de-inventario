import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/services/api-client.service';
import { PageResponse } from '../../shared/models/page-response.model';
import { AuditLog, AuditSearchCriteria } from './audit.models';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly api = inject(ApiClient);

  search(criteria: AuditSearchCriteria): Observable<PageResponse<AuditLog>> {
    const query = new URLSearchParams({
      page: criteria.page.toString(),
      size: criteria.size.toString(),
    });
    if (criteria.action !== null) query.set('action', criteria.action);
    if (criteria.entityName.trim()) query.set('entityName', criteria.entityName.trim());
    if (criteria.userId !== null) query.set('userId', criteria.userId.toString());
    if (criteria.from) query.set('from', normalizeLocalDateTime(criteria.from));
    if (criteria.to) query.set('to', normalizeLocalDateTime(criteria.to));
    return this.api.get<PageResponse<AuditLog>>(`/api/audit-logs?${query.toString()}`);
  }

  findById(id: number): Observable<AuditLog> {
    return this.api.get<AuditLog>(`/api/audit-logs/${id}`);
  }
}

function normalizeLocalDateTime(value: string): string {
  return value.length === 16 ? `${value}:00` : value;
}
