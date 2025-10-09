import {
  HttpClient,
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest
} from '@angular/common/http';
import { AuthenticationService } from "../service/authentication.service";
import { catchError, Observable, switchMap, throwError } from "rxjs";
import { Injectable } from "@angular/core";
import {Router} from "@angular/router";

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  private isRefreshing = false;

  constructor(
    private authenticationService: AuthenticationService,
    private http: HttpClient,
    private router: Router
  ) {}

  intercept(httpRequest: HttpRequest<any>, handler: HttpHandler): Observable<HttpEvent<any>> {
    const publicEndpoints = [
      '/user/login',
      '/user/logout',
      '/user/register',
      '/user/me',
      '/user/refresh'
    ];

    if (publicEndpoints.some(endpoint => httpRequest.url.includes(endpoint))) {
      const request = httpRequest.clone({ withCredentials: true });
      return handler.handle(request);
    }

    const request = httpRequest.clone({ withCredentials: true });

    return handler.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if ((error.status === 401)
          && !this.isRefreshing
          && !httpRequest.url.includes('/user/login')
          && !httpRequest.url.includes('/user/refresh')) {

          this.isRefreshing = true;

          return this.http.post(`${this.authenticationService.host}/user/refresh`, {}, { withCredentials: true }).pipe(
            switchMap(() => {
              this.isRefreshing = false;
              // retry original request
              return handler.handle(request.clone({ withCredentials: true }));
            }),
            catchError(err => {
              this.isRefreshing = false;
              this.router.navigate(['/login']);
              return throwError(() => err);
            })
          );
        }
        return throwError(() => error);
      })
    );
  }
}
