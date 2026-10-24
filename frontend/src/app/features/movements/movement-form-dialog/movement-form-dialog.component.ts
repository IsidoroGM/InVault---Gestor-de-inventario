import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { catchError, finalize, of, switchMap, tap } from 'rxjs';

import { apiErrorMessage, apiFieldErrors } from '../../../shared/utils/api-error-message';
import {
  MovementBatchOption,
  MovementFormOptions,
  MovementType,
  MOVEMENT_TYPE_LABELS,
  MOVEMENT_TYPES,
  StockMovement,
  StockMovementRequest,
  movementDirection,
} from '../movement.models';
import { MovementService } from '../movement.service';

@Component({
  selector: 'app-movement-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './movement-form-dialog.component.html',
  styleUrl: './movement-form-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovementFormDialogComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogRef = inject(MatDialogRef<MovementFormDialogComponent, StockMovement>);
  private readonly service = inject(MovementService);
  protected readonly data = inject<MovementFormOptions>(MAT_DIALOG_DATA);
  protected readonly batches = signal<readonly MovementBatchOption[]>([]);
  protected readonly loadingBatches = signal(false);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly typeLabels = MOVEMENT_TYPE_LABELS;
  protected readonly movementTypes = MOVEMENT_TYPES;

  protected readonly form = new FormGroup({
    productId: new FormControl<number | null>(null, [Validators.required]),
    batchId: new FormControl<number | null>(null, [Validators.required]),
    movementType: new FormControl<MovementType>('INBOUND', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    quantity: new FormControl<number | null>(null, [Validators.required, Validators.min(0.001)]),
    supplierId: new FormControl<number | null>(null),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(255)] }),
  });

  constructor() {
    this.form.controls.productId.valueChanges
      .pipe(
        tap(() => {
          this.form.controls.batchId.setValue(null);
          this.batches.set([]);
          this.errorMessage.set(null);
        }),
        switchMap((productId) => {
          this.loadingBatches.set(productId !== null);
          if (productId === null) {
            return of<readonly MovementBatchOption[]>([]);
          }
          return this.service.loadBatches(productId).pipe(
            catchError((error: unknown) => {
              this.errorMessage.set(apiErrorMessage(error, 'No se pudieron cargar los lotes.'));
              return of<readonly MovementBatchOption[]>([]);
            }),
            finalize(() => this.loadingBatches.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((batches) => this.batches.set(batches));
  }

  protected selectedBatch(): MovementBatchOption | null {
    const id = this.form.controls.batchId.value;
    return this.batches().find((batch) => batch.id === id) ?? null;
  }

  protected batchDisabled(batch: MovementBatchOption): boolean {
    if (batch.status === 'BLOCKED' || batch.status === 'INACTIVE') {
      return true;
    }
    return (
      batch.status === 'CONSUMED' &&
      movementDirection(this.form.controls.movementType.value) === 'negative'
    );
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: StockMovementRequest = {
      productId: value.productId as number,
      batchId: value.batchId as number,
      supplierId: value.supplierId,
      movementType: value.movementType,
      quantity: value.quantity as number,
      reason: value.reason.trim() || null,
    };
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.service
      .create(request)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (movement) => this.dialogRef.close(movement),
        error: (error: unknown) => {
          this.applyFieldErrors(apiFieldErrors(error));
          this.errorMessage.set(
            apiErrorMessage(
              error,
              'No se pudo registrar el movimiento.',
              'Tu rol no permite modificar el stock.',
            ),
          );
        },
      });
  }

  protected serverError(controlName: keyof typeof this.form.controls): string | null {
    return (this.form.controls[controlName].getError('server') as string | undefined) ?? null;
  }

  private applyFieldErrors(errors: Readonly<Record<string, string>>): void {
    for (const [name, message] of Object.entries(errors)) {
      if (name in this.form.controls) {
        const control = this.form.controls[name as keyof typeof this.form.controls];
        control.setErrors({ ...control.errors, server: message });
      }
    }
  }
}
