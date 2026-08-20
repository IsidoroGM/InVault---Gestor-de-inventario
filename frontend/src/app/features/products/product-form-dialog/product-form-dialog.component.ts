import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { finalize } from 'rxjs';

import { Product, ProductCatalogs, ProductRequest } from '../product.models';
import { apiFieldErrors, productErrorMessage } from '../product-error-message';
import { ProductService } from '../product.service';

export interface ProductFormDialogData {
  readonly product: Product | null;
  readonly catalogs: ProductCatalogs;
}

@Component({
  selector: 'app-product-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSlideToggleModule,
  ],
  templateUrl: './product-form-dialog.component.html',
  styleUrl: './product-form-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductFormDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ProductFormDialogComponent, Product>);
  private readonly service = inject(ProductService);
  protected readonly data = inject<ProductFormDialogData>(MAT_DIALOG_DATA);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly editing = this.data.product !== null;

  protected readonly form = new FormGroup({
    sku: new FormControl(this.data.product?.sku ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    name: new FormControl(this.data.product?.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(150)],
    }),
    description: new FormControl(this.data.product?.description ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(500)],
    }),
    categoryId: new FormControl<number | null>(this.data.product?.categoryId ?? null, [
      Validators.required,
    ]),
    locationId: new FormControl<number | null>(this.data.product?.locationId ?? null, [
      Validators.required,
    ]),
    unitId: new FormControl<number | null>(this.data.product?.unitId ?? null, [
      Validators.required,
    ]),
    minimumStock: new FormControl(this.data.product?.minimumStock ?? 0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0)],
    }),
    active: new FormControl(this.data.product?.active ?? true, { nonNullable: true }),
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.request();
    const operation = this.data.product
      ? this.service.update(this.data.product.id, request)
      : this.service.create(request);

    this.clearServerErrors();
    this.errorMessage.set(null);
    this.submitting.set(true);
    operation.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (product) => this.dialogRef.close(product),
      error: (error: unknown) => {
        this.applyFieldErrors(apiFieldErrors(error));
        this.errorMessage.set(productErrorMessage(error, 'No se pudo guardar el producto.'));
      },
    });
  }

  protected serverError(controlName: keyof typeof this.form.controls): string | null {
    return (this.form.controls[controlName].getError('server') as string | undefined) ?? null;
  }

  private request(): ProductRequest {
    const value = this.form.getRawValue();
    return {
      sku: value.sku.trim().toUpperCase(),
      name: value.name.trim(),
      description: value.description.trim() || null,
      categoryId: value.categoryId as number,
      locationId: value.locationId as number,
      unitId: value.unitId as number,
      minimumStock: value.minimumStock,
      active: value.active,
    };
  }

  private applyFieldErrors(errors: Readonly<Record<string, string>>): void {
    for (const [name, message] of Object.entries(errors)) {
      if (name in this.form.controls) {
        const control = this.form.controls[name as keyof typeof this.form.controls];
        control.setErrors({ ...control.errors, server: message });
      }
    }
  }

  private clearServerErrors(): void {
    for (const control of Object.values(this.form.controls)) {
      if (!control.hasError('server')) {
        continue;
      }
      const { server: _server, ...remaining } = control.errors ?? {};
      control.setErrors(Object.keys(remaining).length > 0 ? remaining : null);
    }
  }
}
