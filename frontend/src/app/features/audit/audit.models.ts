export type AuditAction =
  | 'CREATED'
  | 'UPDATED'
  | 'ACTIVATED'
  | 'DEACTIVATED'
  | 'DELETED'
  | 'STOCK_MOVEMENT_CREATED'
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILED'
  | 'LOGOUT'
  | 'PASSWORD_CHANGED'
  | 'PASSWORD_RESET';

export interface AuditLog {
  readonly id: number;
  readonly userId: number | null;
  readonly username: string | null;
  readonly action: AuditAction;
  readonly entityName: string;
  readonly entityId: number | null;
  readonly details: string | null;
  readonly beforeData: string | null;
  readonly afterData: string | null;
  readonly clientIp: string | null;
  readonly createdAt: string;
}

export interface AuditSearchCriteria {
  readonly action: AuditAction | null;
  readonly entityName: string;
  readonly userId: number | null;
  readonly from: string;
  readonly to: string;
  readonly page: number;
  readonly size: number;
}

export const AUDIT_ACTION_LABELS: Readonly<Record<AuditAction, string>> = {
  CREATED: 'Creación',
  UPDATED: 'Actualización',
  ACTIVATED: 'Activación',
  DEACTIVATED: 'Desactivación',
  DELETED: 'Eliminación',
  STOCK_MOVEMENT_CREATED: 'Movimiento de stock',
  LOGIN_SUCCESS: 'Acceso correcto',
  LOGIN_FAILED: 'Acceso fallido',
  LOGOUT: 'Cierre de sesión',
  PASSWORD_CHANGED: 'Cambio de contraseña',
  PASSWORD_RESET: 'Restablecimiento de contraseña',
};

export const AUDIT_ACTIONS = Object.keys(AUDIT_ACTION_LABELS) as readonly AuditAction[];
