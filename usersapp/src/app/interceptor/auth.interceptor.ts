import {HttpEvent, HttpHandler, HttpInterceptor, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {AuthenticationService} from "../service/authentication.service";
import {Observable} from "rxjs";
import {Injectable} from "@angular/core";

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private authenticationService: AuthenticationService){}

  intercept(httpRequest: HttpRequest<any>, handler: HttpHandler): Observable<HttpEvent<any>> {
    if(httpRequest.url.includes(`${this.authenticationService.host}/user/login`)){
      return handler.handle(httpRequest);
    }
    if(httpRequest.url.includes(`${this.authenticationService.host}/user/register`)){
      return handler.handle(httpRequest);
    }
    const request = httpRequest.clone(
      {withCredentials: true},
    )
    return handler.handle(request);

  }
}

