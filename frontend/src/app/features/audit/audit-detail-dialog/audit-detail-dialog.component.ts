import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

import { AUDIT_ACTION_LABELS, AuditAction, AuditLog } from '../audit.models';

@Component({
  selector: 'app-audit-detail-dialog',
  imports: [DatePipe, MatButtonModule, MatDialogModule],
  templateUrl: './audit-detail-dialog.component.html',
  styleUrl: './audit-detail-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditDetailDialogComponent {
  protected readonly entry = inject<AuditLog>(MAT_DIALOG_DATA);
  protected readonly actionLabels = AUDIT_ACTION_LABELS;
  protected readonly before = prettySnapshot(this.entry.beforeData);
  protected readonly after = prettySnapshot(this.entry.afterData);

  protected actionLabel(action: AuditAction): string {
    return this.actionLabels[action];
  }
}

function prettySnapshot(value: string | null): string | null {
  if (!value) return null;
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
