import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { InventoryRealtimeService } from '../../core/realtime/inventory-realtime.service';
import { apiErrorMessage } from '../../shared/utils/api-error-message';
import { BatchFormDialogComponent } from './batch-form-dialog/batch-form-dialog.component';
import { Batch, BatchProductOption, BatchStatus, BATCH_STATUS_LABELS } from './batch.models';
import { BatchService } from './batch.service';

@Component({
  selector: 'app-batches-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    RouterLink,
  ],
  templateUrl: './batches-page.component.html',
  styleUrl: './batches-page.component.scss',
  providers: [{ provide: MatPaginatorIntl, useFactory: batchPaginatorIntl }],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BatchesPageComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);
  private readonly realtime = inject(InventoryRealtimeService);
  private readonly service = inject(BatchService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly sessionStore = inject(AuthSessionStore);
  private lastMovementId: number | null = null;

  protected readonly batches = signal<readonly Batch[]>([]);
  protected readonly products = signal<readonly BatchProductOption[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly statusLabels = BATCH_STATUS_LABELS;
  protected readonly canManage = computed(() =>
    this.sessionStore.hasAnyRole(['ADMIN', 'SUPERVISOR']),
  );
  protected readonly canMoveStock = computed(() =>
    this.sessionStore.hasAnyRole(['ADMIN', 'SUPERVISOR', 'WAREHOUSE']),
  );
  protected readonly displayedColumns = [
    'code',
    'product',
    'quantity',
    'status',
    'notes',
    'actions',
  ];
  protected readonly pageSizes = [10, 25, 50];
  protected pageIndex = 0;
  protected pageSize = 25;
  protected readonly filters = this.formBuilder.group({
    productId: this.formBuilder.control<number | null>(null),
  });

  constructor() {
    effect(() => {
      const event = this.realtime.lastEvent();
      if (event && event.movementId !== this.lastMovementId) {
        this.lastMovementId = event.movementId;
        this.loadBatches();
      }
    });
  }

  ngOnInit(): void {
    this.loadProducts();
    this.loadBatches();
    this.filters.controls.productId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.pageIndex = 0;
        this.loadBatches();
      });
  }

  protected openCreate(): void {
    this.openForm(null);
  }
  protected openEdit(batch: Batch): void {
    this.openForm(batch);
  }
  protected clearProduct(): void {
    this.filters.controls.productId.setValue(null);
  }
  protected pageChanged(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadBatches();
  }
  protected reload(): void {
    this.loadProducts();
    this.loadBatches();
  }

  protected statusLabel(status: BatchStatus): string {
    return this.statusLabels[status];
  }

  private openForm(batch: Batch | null): void {
    if (this.products().length === 0) {
      this.snackBar.open('No hay productos activos disponibles.', 'Cerrar', { duration: 5000 });
      return;
    }
    this.dialog
      .open(BatchFormDialogComponent, {
        data: { batch, products: this.products() },
        width: '48rem',
        maxWidth: 'calc(100vw - 2rem)',
        disableClose: true,
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((saved: Batch | undefined) => {
        if (saved) {
          this.snackBar.open(batch ? 'Lote actualizado.' : 'Lote creado.', 'Cerrar', {
            duration: 3500,
          });
          this.loadBatches();
        }
      });
  }

  private loadProducts(): void {
    this.service
      .loadActiveProducts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (products) => this.products.set(products),
        error: (error: unknown) =>
          this.snackBar.open(
            apiErrorMessage(error, 'No se pudieron cargar los productos.'),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }

  private loadBatches(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service
      .search({
        productId: this.filters.controls.productId.value,
        page: this.pageIndex,
        size: this.pageSize,
      })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (page) => {
          this.batches.set(page.content);
          this.totalElements.set(page.totalElements);
        },
        error: (error: unknown) => {
          this.batches.set([]);
          this.totalElements.set(0);
          this.errorMessage.set(apiErrorMessage(error, 'No se pudieron cargar los lotes.'));
        },
      });
  }
}

function batchPaginatorIntl(): MatPaginatorIntl {
  const paginator = new MatPaginatorIntl();
  paginator.itemsPerPageLabel = 'Lotes por página:';
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
