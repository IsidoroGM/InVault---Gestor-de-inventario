import { Role } from '../../shared/models/role.model';

export interface LoginRequest {
  readonly username: string;
  readonly password: string;
}

export interface LoginResponse {
  readonly accessToken: string;
  readonly tokenType: string;
  readonly expiresIn: number;
  readonly expiresAt: string;
  readonly userId: number;
  readonly username: string;
  readonly roles: readonly Role[];
  readonly mustChangePassword: boolean;
}

export interface AuthSession extends LoginResponse {}

export interface PasswordChangeRequest {
  readonly currentPassword: string;
  readonly newPassword: string;
}
