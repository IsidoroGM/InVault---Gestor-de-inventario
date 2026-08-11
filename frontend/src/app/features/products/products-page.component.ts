import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, finalize } from 'rxjs';

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { ProductDeactivateDialogComponent } from './product-deactivate-dialog.component';
import { productErrorMessage } from './product-error-message';
import { ProductFormDialogComponent } from './product-form-dialog/product-form-dialog.component';
import { Product, ProductActiveFilter, ProductCatalogs } from './product.models';
import { ProductService } from './product.service';

@Component({
  selector: 'app-products-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './products-page.component.html',
  styleUrl: './products-page.component.scss',
  providers: [{ provide: MatPaginatorIntl, useFactory: createSpanishPaginatorIntl }],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductsPageComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);
  private readonly service = inject(ProductService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly sessionStore = inject(AuthSessionStore);
  private requestSequence = 0;

  protected readonly displayedColumns = [
    'sku',
    'product',
    'classification',
    'minimumStock',
    'status',
    'actions',
  ];
  protected readonly pageSizes = [10, 25, 50];
  protected readonly products = signal<readonly Product[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly catalogs = signal<ProductCatalogs | null>(null);
  protected readonly catalogsLoading = signal(false);
  protected readonly canManage = computed(() =>
    this.sessionStore.hasAnyRole(['ADMIN', 'SUPERVISOR']),
  );

  protected pageIndex = 0;
  protected pageSize = 25;
  protected readonly filters = this.formBuilder.nonNullable.group({
    query: '',
    active: 'all' as ProductActiveFilter,
  });

  ngOnInit(): void {
    this.loadProducts();
    if (this.canManage()) {
      this.loadCatalogs();
    }

    this.filters.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(
          (previous, current) =>
            previous.query === current.query && previous.active === current.active,
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.pageIndex = 0;
        this.loadProducts();
      });
  }

  protected reload(): void {
    this.loadProducts();
    if (this.canManage() && !this.catalogs()) {
      this.loadCatalogs();
    }
  }

  protected clearFilters(): void {
    this.filters.setValue({ query: '', active: 'all' });
  }

  protected pageChanged(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadProducts();
  }

  protected openCreate(): void {
    this.openForm(null);
  }

  protected openEdit(product: Product): void {
    this.openForm(product);
  }

  protected confirmDeactivate(product: Product): void {
    this.dialog
      .open<ProductDeactivateDialogComponent, Product, boolean>(ProductDeactivateDialogComponent, {
        data: product,
        width: '28rem',
        maxWidth: 'calc(100vw - 2rem)',
        autoFocus: false,
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) {
          this.deactivate(product);
        }
      });
  }

  private openForm(product: Product | null): void {
    const catalogs = this.catalogs();
    if (!catalogs) {
      this.snackBar.open('Los catálogos aún no están disponibles. Vuelve a intentarlo.', 'Cerrar', {
        duration: 5000,
      });
      this.loadCatalogs();
      return;
    }

    this.dialog
      .open<
        ProductFormDialogComponent,
        { product: Product | null; catalogs: ProductCatalogs },
        Product
      >(ProductFormDialogComponent, {
        data: { product, catalogs },
        width: '50rem',
        maxWidth: 'calc(100vw - 2rem)',
        disableClose: true,
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((savedProduct) => {
        if (!savedProduct) {
          return;
        }
        this.snackBar.open(product ? 'Producto actualizado.' : 'Producto creado.', 'Cerrar', {
          duration: 3500,
        });
        this.loadProducts();
      });
  }

  private deactivate(product: Product): void {
    this.service
      .deactivate(product.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.snackBar.open('Producto desactivado.', 'Cerrar', { duration: 3500 });
          this.loadProducts();
        },
        error: (error: unknown) =>
          this.snackBar.open(
            productErrorMessage(error, 'No se pudo desactivar el producto.'),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }

  private loadProducts(): void {
    const requestId = ++this.requestSequence;
    const filters = this.filters.getRawValue();
    this.loading.set(true);
    this.errorMessage.set(null);

    this.service
      .search({
        query: filters.query,
        active: filters.active,
        page: this.pageIndex,
        size: this.pageSize,
      })
      .pipe(
        finalize(() => {
          if (requestId === this.requestSequence) {
            this.loading.set(false);
          }
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (page) => {
          if (requestId !== this.requestSequence) {
            return;
          }
          if (page.content.length === 0 && this.pageIndex > 0 && page.totalElements > 0) {
            this.pageIndex -= 1;
            this.loadProducts();
            return;
          }
          this.products.set(page.content);
          this.totalElements.set(page.totalElements);
        },
        error: (error: unknown) => {
          if (requestId !== this.requestSequence) {
            return;
          }
          this.products.set([]);
          this.totalElements.set(0);
          this.errorMessage.set(
            productErrorMessage(error, 'No se pudo cargar el catálogo de productos.'),
          );
        },
      });
  }

  private loadCatalogs(): void {
    if (this.catalogsLoading()) {
      return;
    }
    this.catalogsLoading.set(true);
    this.service
      .loadCatalogs()
      .pipe(
        finalize(() => this.catalogsLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (catalogs) => this.catalogs.set(catalogs),
        error: (error: unknown) =>
          this.snackBar.open(
            productErrorMessage(error, 'No se pudieron cargar los catálogos del formulario.'),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }
}

function createSpanishPaginatorIntl(): MatPaginatorIntl {
  const paginator = new MatPaginatorIntl();
  paginator.itemsPerPageLabel = 'Productos por página:';
  paginator.firstPageLabel = 'Primera página';
  paginator.previousPageLabel = 'Página anterior';
  paginator.nextPageLabel = 'Página siguiente';
  paginator.lastPageLabel = 'Última página';
  paginator.getRangeLabel = (page, pageSize, length) => {
    if (length === 0 || pageSize === 0) {
      return `0 de ${length}`;
    }
    const start = page * pageSize;
    const end = Math.min(start + pageSize, length);
    return `${start + 1}–${end} de ${length}`;
  };
  return paginator;
}
