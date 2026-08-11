import { Role } from '../../shared/models/role.model';

export interface UserAccount {
  readonly id: number;
  readonly username: string;
  readonly email: string;
  readonly active: boolean;
  readonly mustChangePassword: boolean;
  readonly lastLoginAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string | null;
  readonly roles: readonly Role[];
}

export interface UserCreateRequest {
  readonly username: string;
  readonly email: string;
  readonly temporaryPassword: string;
  readonly roles: readonly Role[];
}

export interface UserUpdateRequest {
  readonly email: string;
  readonly roles: readonly Role[];
}

export interface PasswordResetRequest {
  readonly temporaryPassword: string;
}

export interface RoleOption {
  readonly id: number;
  readonly name: Role;
  readonly description: string;
  readonly active: boolean;
}

export type UserActiveFilter = 'all' | 'active' | 'inactive';

export const ROLE_LABELS: Readonly<Record<Role, string>> = {
  ADMIN: 'Administración',
  SUPERVISOR: 'Supervisión',
  WAREHOUSE: 'Almacén',
  READ_ONLY: 'Solo lectura',
};
