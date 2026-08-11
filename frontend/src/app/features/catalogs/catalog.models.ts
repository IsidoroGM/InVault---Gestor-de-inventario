export type CatalogKind = 'categories' | 'locations' | 'units' | 'suppliers';
export type CatalogActiveFilter = 'all' | 'active' | 'inactive';

export interface CatalogDefinition {
  readonly kind: CatalogKind;
  readonly singular: string;
  readonly newLabel: string;
  readonly plural: string;
  readonly description: string;
}

export const CATALOG_DEFINITIONS: readonly CatalogDefinition[] = [
  {
    kind: 'categories',
    singular: 'categoría',
    newLabel: 'Nueva categoría',
    plural: 'Categorías',
    description: 'Clasificación funcional de los productos.',
  },
  {
    kind: 'locations',
    singular: 'ubicación',
    newLabel: 'Nueva ubicación',
    plural: 'Ubicaciones',
    description: 'Zonas y espacios principales de almacenamiento.',
  },
  {
    kind: 'units',
    singular: 'unidad',
    newLabel: 'Nueva unidad',
    plural: 'Unidades',
    description: 'Unidades de medida utilizadas en el inventario.',
  },
  {
    kind: 'suppliers',
    singular: 'proveedor',
    newLabel: 'Nuevo proveedor',
    plural: 'Proveedores',
    description: 'Empresas y contactos asociados al abastecimiento.',
  },
];

export interface BaseCatalogItem {
  readonly id: number;
  readonly name: string;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface NamedCatalogItem extends BaseCatalogItem {
  readonly description: string | null;
}

export interface UnitCatalogItem extends NamedCatalogItem {
  readonly code: string;
  readonly symbol: string | null;
}

export interface SupplierCatalogItem extends BaseCatalogItem {
  readonly contactName: string | null;
  readonly phone: string | null;
  readonly email: string | null;
  readonly notes: string | null;
}

export interface NamedCatalogRequest {
  readonly name: string;
  readonly description: string | null;
  readonly active: boolean;
}

export interface UnitCatalogRequest extends NamedCatalogRequest {
  readonly code: string;
  readonly symbol: string | null;
}

export interface SupplierCatalogRequest {
  readonly name: string;
  readonly contactName: string | null;
  readonly phone: string | null;
  readonly email: string | null;
  readonly notes: string | null;
  readonly active: boolean;
}

export interface CatalogItemMap {
  readonly categories: NamedCatalogItem;
  readonly locations: NamedCatalogItem;
  readonly units: UnitCatalogItem;
  readonly suppliers: SupplierCatalogItem;
}

export interface CatalogRequestMap {
  readonly categories: NamedCatalogRequest;
  readonly locations: NamedCatalogRequest;
  readonly units: UnitCatalogRequest;
  readonly suppliers: SupplierCatalogRequest;
}

export type CatalogItem = CatalogItemMap[CatalogKind];
export type CatalogRequest = CatalogRequestMap[CatalogKind];

export interface CatalogCollections {
  readonly categories: readonly NamedCatalogItem[];
  readonly locations: readonly NamedCatalogItem[];
  readonly units: readonly UnitCatalogItem[];
  readonly suppliers: readonly SupplierCatalogItem[];
}
