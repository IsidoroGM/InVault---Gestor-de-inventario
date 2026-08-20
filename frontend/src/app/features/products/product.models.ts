export interface Product {
  readonly id: number;
  readonly sku: string;
  readonly name: string;
  readonly description: string | null;
  readonly categoryId: number;
  readonly categoryName: string;
  readonly locationId: number;
  readonly locationName: string;
  readonly unitId: number;
  readonly unitCode: string;
  readonly unitName: string;
  readonly unitSymbol: string;
  readonly minimumStock: number;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface ProductRequest {
  readonly sku: string;
  readonly name: string;
  readonly description: string | null;
  readonly categoryId: number;
  readonly locationId: number;
  readonly unitId: number;
  readonly minimumStock: number;
  readonly active: boolean;
}

export interface NamedCatalogOption {
  readonly id: number;
  readonly name: string;
  readonly active: boolean;
}

export interface UnitCatalogOption extends NamedCatalogOption {
  readonly code: string;
  readonly symbol: string;
}

export interface ProductCatalogs {
  readonly categories: readonly NamedCatalogOption[];
  readonly locations: readonly NamedCatalogOption[];
  readonly units: readonly UnitCatalogOption[];
}

export type ProductActiveFilter = 'all' | 'active' | 'inactive';

export interface ProductSearchCriteria {
  readonly query: string;
  readonly active: ProductActiveFilter;
  readonly page: number;
  readonly size: number;
}
