export interface ProductStock {
  readonly productId: number;
  readonly sku: string;
  readonly productName: string;
  readonly unitCode: string;
  readonly minimumStock: number;
  readonly currentStock: number;
  readonly lowStock: boolean;
}
