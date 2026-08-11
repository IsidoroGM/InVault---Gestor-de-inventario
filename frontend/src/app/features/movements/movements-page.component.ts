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
import { DatePipe } from '@angular/common';
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
import { MovementFormDialogComponent } from './movement-form-dialog/movement-form-dialog.component';
import {
  MovementFormOptions,
  MovementType,
  MOVEMENT_TYPE_LABELS,
  MOVEMENT_TYPES,
  StockMovement,
  movementDirection,
} from './movement.models';
import { MovementService } from './movement.service';

@Component({
  selector: 'app-movements-page',
  imports: [
    DatePipe,
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
  templateUrl: './movements-page.component.html',
  styleUrl: './movements-page.component.scss',
  providers: [{ provide: MatPaginatorIntl, useFactory: movementPaginatorIntl }],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovementsPageComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);
  private readonly realtime = inject(InventoryRealtimeService);
  private readonly service = inject(MovementService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly sessionStore = inject(AuthSessionStore);
  private lastMovementId: number | null = null;

  protected readonly movements = signal<readonly StockMovement[]>([]);
  protected readonly options = signal<MovementFormOptions>({ products: [], suppliers: [] });
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly canManage = computed(() =>
    this.sessionStore.hasAnyRole(['ADMIN', 'SUPERVISOR', 'WAREHOUSE']),
  );
  protected readonly typeLabels = MOVEMENT_TYPE_LABELS;
  protected readonly movementTypes = MOVEMENT_TYPES;
  protected readonly displayedColumns = [
    'date',
    'type',
    'product',
    'batch',
    'quantity',
    'balance',
    'user',
  ];
  protected readonly pageSizes = [10, 25, 50];
  protected pageIndex = 0;
  protected pageSize = 25;
  protected readonly filters = this.formBuilder.group({
    productId: this.formBuilder.control<number | null>(null),
    movementType: this.formBuilder.control<MovementType | null>(null),
  });

  constructor() {
    effect(() => {
      const event = this.realtime.lastEvent();
      if (event && event.movementId !== this.lastMovementId) {
        this.lastMovementId = event.movementId;
        this.loadMovements();
      }
    });
  }

  ngOnInit(): void {
    this.loadOptions();
    this.loadMovements();
    this.filters.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageIndex = 0;
      this.loadMovements();
    });
  }

  protected direction(type: MovementType): 'positive' | 'negative' {
    return movementDirection(type);
  }
  protected typeLabel(type: MovementType): string {
    return this.typeLabels[type];
  }
  protected clearFilters(): void {
    this.filters.reset({ productId: null, movementType: null });
  }
  protected pageChanged(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadMovements();
  }
  protected reload(): void {
    this.loadOptions();
    this.loadMovements();
  }

  protected openCreate(): void {
    if (this.options().products.length === 0) {
      this.snackBar.open('No hay productos activos disponibles.', 'Cerrar', { duration: 5000 });
      return;
    }
    this.dialog
      .open(MovementFormDialogComponent, {
        data: this.options(),
        width: '50rem',
        maxWidth: 'calc(100vw - 2rem)',
        disableClose: true,
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((movement: StockMovement | undefined) => {
        if (movement) {
          this.snackBar.open(
            `Movimiento #${movement.id} registrado. Nuevo saldo: ${movement.newBatchQuantity}.`,
            'Cerrar',
            { duration: 5000 },
          );
          this.loadMovements();
        }
      });
  }

  private loadOptions(): void {
    this.service
      .loadFormOptions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (options) => this.options.set(options),
        error: (error: unknown) =>
          this.snackBar.open(
            apiErrorMessage(error, 'No se pudieron cargar las opciones.'),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }

  private loadMovements(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service
      .search({
        productId: this.filters.controls.productId.value,
        movementType: this.filters.controls.movementType.value,
        page: this.pageIndex,
        size: this.pageSize,
      })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (page) => {
          this.movements.set(page.content);
          this.totalElements.set(page.totalElements);
        },
        error: (error: unknown) => {
          this.movements.set([]);
          this.totalElements.set(0);
          this.errorMessage.set(apiErrorMessage(error, 'No se pudieron cargar los movimientos.'));
        },
      });
  }
}

function movementPaginatorIntl(): MatPaginatorIntl {
  const paginator = new MatPaginatorIntl();
  paginator.itemsPerPageLabel = 'Movimientos por página:';
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
