import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_CONFIGURATION } from '../configuration/api-configuration';

@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly configuration = inject(API_CONFIGURATION);

  get<T>(path: string): Observable<T> {
    return this.http.get<T>(this.url(path));
  }

  getText(path: string): Observable<string> {
    return this.http.get(this.url(path), { responseType: 'text' });
  }

  post<Request, Response>(path: string, body: Request): Observable<Response> {
    return this.http.post<Response>(this.url(path), body);
  }

  put<Request, Response>(path: string, body: Request): Observable<Response> {
    return this.http.put<Response>(this.url(path), body);
  }

  patch<Request, Response>(path: string, body: Request): Observable<Response> {
    return this.http.patch<Response>(this.url(path), body);
  }

  delete(path: string): Observable<void> {
    return this.http.delete<void>(this.url(path));
  }

  private url(path: string): string {
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    return `${this.configuration.restBaseUrl}${normalizedPath}`;
  }
}
