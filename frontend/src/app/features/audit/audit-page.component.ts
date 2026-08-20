import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../shared/utils/api-error-message';
import { UserAccount } from '../users/user.models';
import { UserService } from '../users/user.service';
import { AuditDetailDialogComponent } from './audit-detail-dialog/audit-detail-dialog.component';
import { AUDIT_ACTION_LABELS, AUDIT_ACTIONS, AuditAction, AuditLog } from './audit.models';
import { AuditService } from './audit.service';

@Component({
  selector: 'app-audit-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './audit-page.component.html',
  styleUrl: './audit-page.component.scss',
  providers: [{ provide: MatPaginatorIntl, useFactory: auditPaginatorIntl }],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditPageComponent implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly service = inject(AuditService);
  private readonly userService = inject(UserService);

  protected readonly entries = signal<readonly AuditLog[]>([]);
  protected readonly users = signal<readonly UserAccount[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly actionLabels = AUDIT_ACTION_LABELS;
  protected readonly actions = AUDIT_ACTIONS;
  protected readonly displayedColumns = [
    'date',
    'action',
    'actor',
    'entity',
    'details',
    'ip',
    'actions',
  ];
  protected readonly pageSizes = [10, 25, 50, 100];
  protected pageIndex = 0;
  protected pageSize = 25;
  protected readonly filters = new FormGroup({
    action: new FormControl<AuditAction | null>(null),
    entityName: new FormControl('', { nonNullable: true }),
    userId: new FormControl<number | null>(null),
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
  });

  ngOnInit(): void {
    this.userService.findAll().subscribe({ next: (users) => this.users.set(users) });
    this.loadEntries();
  }

  protected actionLabel(action: AuditAction): string {
    return this.actionLabels[action];
  }
  protected dateRangeInvalid(): boolean {
    const { from, to } = this.filters.getRawValue();
    return Boolean(from && to && from > to);
  }
  protected applyFilters(): void {
    if (this.dateRangeInvalid()) return;
    this.pageIndex = 0;
    this.loadEntries();
  }
  protected clearFilters(): void {
    this.filters.reset({ action: null, entityName: '', userId: null, from: '', to: '' });
    this.pageIndex = 0;
    this.loadEntries();
  }
  protected pageChanged(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadEntries();
  }
  protected reload(): void {
    this.loadEntries();
  }
  protected openDetail(entry: AuditLog): void {
    this.dialog.open(AuditDetailDialogComponent, {
      data: entry,
      width: '66rem',
      maxWidth: 'calc(100vw - 2rem)',
    });
  }

  private loadEntries(): void {
    const filters = this.filters.getRawValue();
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service
      .search({ ...filters, page: this.pageIndex, size: this.pageSize })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (page) => {
          this.entries.set(page.content);
          this.totalElements.set(page.totalElements);
        },
        error: (error: unknown) => {
          this.entries.set([]);
          this.totalElements.set(0);
          this.errorMessage.set(
            apiErrorMessage(error, 'No se pudieron cargar los registros de auditoría.'),
          );
        },
      });
  }
}

function auditPaginatorIntl(): MatPaginatorIntl {
  const paginator = new MatPaginatorIntl();
  paginator.itemsPerPageLabel = 'Registros por página:';
  paginator.firstPageLabel = 'Primera página';
  paginator.previousPageLabel = 'Página anterior';
  paginator.nextPageLabel = 'Página siguiente';
  paginator.lastPageLabel = 'Última página';
  paginator.getRangeLabel = (page, size, length) =>
    length === 0
      ? '0 de 0'
      : `${page * size + 1}–${Math.min((page + 1) * size, length)} de ${length}`;
  return paginator;
}
