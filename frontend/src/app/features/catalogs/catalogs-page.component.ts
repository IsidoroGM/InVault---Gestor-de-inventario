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
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { debounceTime, finalize } from 'rxjs';

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import {
  CatalogDeactivateDialogComponent,
  CatalogDeactivateDialogData,
} from './catalog-deactivate-dialog.component';
import { catalogErrorMessage } from './catalog-error-message';
import { CatalogFormDialogComponent } from './catalog-form-dialog/catalog-form-dialog.component';
import {
  CatalogActiveFilter,
  CatalogCollections,
  CatalogDefinition,
  CatalogItem,
  CatalogKind,
  CATALOG_DEFINITIONS,
  SupplierCatalogItem,
  UnitCatalogItem,
} from './catalog.models';
import { CatalogService } from './catalog.service';

const EMPTY_COLLECTIONS: CatalogCollections = {
  categories: [],
  locations: [],
  units: [],
  suppliers: [],
};

@Component({
  selector: 'app-catalogs-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTabsModule,
  ],
  templateUrl: './catalogs-page.component.html',
  styleUrl: './catalogs-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogsPageComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);
  private readonly service = inject(CatalogService);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly definitions = CATALOG_DEFINITIONS;
  protected readonly collections = signal<CatalogCollections>(EMPTY_COLLECTIONS);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly selectedIndex = signal(0);
  protected readonly query = signal('');
  protected readonly activeFilter = signal<CatalogActiveFilter>('all');
  protected readonly canManage = computed(() =>
    this.sessionStore.hasAnyRole(['ADMIN', 'SUPERVISOR']),
  );
  protected readonly currentDefinition = computed(
    () => this.definitions[this.selectedIndex()] ?? this.definitions[0],
  );
  protected readonly currentItems = computed<readonly CatalogItem[]>(() => {
    const definition = this.currentDefinition();
    const items = this.collections()[definition.kind] as readonly CatalogItem[];
    const query = this.query().trim().toLocaleLowerCase('es');
    const active = this.activeFilter();
    return items.filter((item) => {
      const matchesState = active === 'all' || (active === 'active' ? item.active : !item.active);
      const matchesQuery = !query || searchableText(item).includes(query);
      return matchesState && matchesQuery;
    });
  });

  protected readonly filters = this.formBuilder.nonNullable.group({
    query: '',
    active: 'all' as CatalogActiveFilter,
  });

  ngOnInit(): void {
    this.loadAll();
    this.filters.valueChanges
      .pipe(debounceTime(250), takeUntilDestroyed(this.destroyRef))
      .subscribe((filters) => {
        this.query.set(filters.query ?? '');
        this.activeFilter.set(filters.active ?? 'all');
      });
  }

  protected selectTab(index: number): void {
    if (index === this.selectedIndex()) {
      return;
    }
    this.selectedIndex.set(index);
    this.clearFilters();
  }

  protected clearFilters(): void {
    this.filters.setValue({ query: '', active: 'all' });
    this.query.set('');
    this.activeFilter.set('all');
  }

  protected count(kind: CatalogKind): number {
    return this.collections()[kind].length;
  }

  protected unit(item: CatalogItem): UnitCatalogItem {
    return item as UnitCatalogItem;
  }

  protected supplier(item: CatalogItem): SupplierCatalogItem {
    return item as SupplierCatalogItem;
  }

  protected openCreate(): void {
    this.openForm(null);
  }

  protected openEdit(item: CatalogItem): void {
    this.openForm(item);
  }

  protected confirmDeactivate(item: CatalogItem): void {
    const definition = this.currentDefinition();
    this.dialog
      .open<CatalogDeactivateDialogComponent, CatalogDeactivateDialogData, boolean>(
        CatalogDeactivateDialogComponent,
        {
          data: { item, definition },
          width: '28rem',
          maxWidth: 'calc(100vw - 2rem)',
          autoFocus: false,
        },
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) {
          this.deactivate(definition, item);
        }
      });
  }

  protected reload(): void {
    this.loadAll();
  }

  private openForm(item: CatalogItem | null): void {
    const definition = this.currentDefinition();
    this.dialog
      .open<
        CatalogFormDialogComponent,
        { kind: CatalogKind; item: CatalogItem | null },
        CatalogItem
      >(CatalogFormDialogComponent, {
        data: { kind: definition.kind, item },
        width: '48rem',
        maxWidth: 'calc(100vw - 2rem)',
        disableClose: true,
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((savedItem) => {
        if (!savedItem) {
          return;
        }
        this.snackBar.open(item ? 'Registro actualizado.' : 'Registro creado.', 'Cerrar', {
          duration: 3500,
        });
        this.loadAll();
      });
  }

  private deactivate(definition: CatalogDefinition, item: CatalogItem): void {
    this.service
      .deactivate(definition.kind, item.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.snackBar.open('Registro desactivado.', 'Cerrar', { duration: 3500 });
          this.loadAll();
        },
        error: (error: unknown) =>
          this.snackBar.open(
            catalogErrorMessage(error, 'No se pudo desactivar el registro.'),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }

  private loadAll(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service
      .loadAll()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (collections) => this.collections.set(collections),
        error: (error: unknown) => {
          this.collections.set(EMPTY_COLLECTIONS);
          this.errorMessage.set(catalogErrorMessage(error, 'No se pudieron cargar los catálogos.'));
        },
      });
  }
}

function searchableText(item: CatalogItem): string {
  return Object.values(item)
    .filter((value): value is string => typeof value === 'string')
    .join(' ')
    .toLocaleLowerCase('es');
}
