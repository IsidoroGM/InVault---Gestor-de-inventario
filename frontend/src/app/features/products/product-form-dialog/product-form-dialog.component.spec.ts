import { FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { Product, ProductCatalogs } from '../product.models';
import { ProductService } from '../product.service';
import { ProductFormDialogComponent, ProductFormDialogData } from './product-form-dialog.component';

interface TestableProductFormDialog {
  readonly form: FormGroup;
  submit(): void;
}

describe('ProductFormDialogComponent', () => {
  const catalogs: ProductCatalogs = {
    categories: [{ id: 1, name: 'Electrónica', active: true }],
    locations: [{ id: 2, name: 'Almacén', active: true }],
    units: [{ id: 3, code: 'UD', name: 'Unidad', symbol: 'ud', active: true }],
  };
  const savedProduct = {
    id: 10,
    sku: 'SKU-01',
    name: 'Cable USB',
  } as Product;
  const service = {
    create: vi.fn(() => of(savedProduct)),
    update: vi.fn(() => of(savedProduct)),
  };
  const dialogRef = { close: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('normalizes and creates a valid product', async () => {
    const component = await createComponent({ product: null, catalogs });
    const testable = component as unknown as TestableProductFormDialog;
    testable.form.setValue({
      sku: ' sku-01 ',
      name: ' Cable USB ',
      description: '  Carga rápida  ',
      categoryId: 1,
      locationId: 2,
      unitId: 3,
      minimumStock: 5,
      active: true,
    });

    testable.submit();

    expect(service.create).toHaveBeenCalledWith({
      sku: 'SKU-01',
      name: 'Cable USB',
      description: 'Carga rápida',
      categoryId: 1,
      locationId: 2,
      unitId: 3,
      minimumStock: 5,
      active: true,
    });
    expect(dialogRef.close).toHaveBeenCalledWith(savedProduct);
  });

  it('does not submit an invalid product', async () => {
    const component = await createComponent({ product: null, catalogs });
    const testable = component as unknown as TestableProductFormDialog;

    testable.submit();

    expect(service.create).not.toHaveBeenCalled();
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(testable.form.controls['sku'].touched).toBe(true);
  });

  const measuredProduct: Product = {
    id: 1,
    sku: 'MATERIAL-001',
    name: 'Material',
    description: null,
    categoryId: 1,
    categoryName: 'Materiales',
    locationId: 2,
    locationName: 'Almacén',
    unitId: 9,
    unitCode: 'KG',
    unitName: 'Kilogramo',
    unitSymbol: 'kg',
    minimumStock: 0,
    active: true,
    createdAt: '',
    updatedAt: '',
  };

  it('preserves a locked unit, including when absent from active catalogues', async () => {
    await createComponent({ product: { ...measuredProduct, unitLocked: true }, catalogs });
    const fixture = TestBed.createComponent(ProductFormDialogComponent);
    const component = fixture.componentInstance as unknown as TestableProductFormDialog;
    expect(component.form.controls['unitId'].disabled).toBe(true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Kilogramo');
    component.form.patchValue({ unitId: 3, name: 'Material revisado' });
    component.submit();
    expect(service.update).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ unitId: 9, name: 'Material revisado' }),
    );
  });

  it('allows an unused product to select another unit', async () => {
    const component = (await createComponent({
      product: { ...measuredProduct, unitLocked: false },
      catalogs,
    })) as unknown as TestableProductFormDialog;
    expect(component.form.controls['unitId'].enabled).toBe(true);
    component.form.patchValue({ unitId: 3 });
    component.submit();
    expect(service.update).toHaveBeenCalledWith(1, expect.objectContaining({ unitId: 3 }));
  });

  async function createComponent(data: ProductFormDialogData): Promise<ProductFormDialogComponent> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ProductFormDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: ProductService, useValue: service },
      ],
    }).compileComponents();

    return TestBed.createComponent(ProductFormDialogComponent).componentInstance;
  }
});
