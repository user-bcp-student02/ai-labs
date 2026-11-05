// auth.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * La forma de la respuesta exitosa no está especificada aquí.
 * El componente la entrega al contenedor sin guardar ni interpretar tokens.
 */
export type LoginResponse = unknown;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/auth/login', credentials);
  }
}