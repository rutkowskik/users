import { Injectable } from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpResponse} from '@angular/common/http';
import {environment} from "../../environments/environment";
import {catchError, map, Observable, of} from "rxjs";
import { User } from '../model/user';
import { JwtHelperService } from "@auth0/angular-jwt";

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {

  public host = environment.apiUrl;
  private loggedInUsername: any;

  constructor(private http: HttpClient) { }

  public login(user: User): Observable<HttpResponse<User>> {
    return this.http.post<User>(`${this.host}/user/login`, user, {observe: 'response', withCredentials: true});
  }

  public register(user: User): Observable<User> {
    return this.http.post<User>(`${this.host}/user/register`, user);
  }

  public logOut(): void {
    this.loggedInUsername = null;
    if (typeof window !== 'undefined' && localStorage){
      localStorage.removeItem('user');
      localStorage.removeItem('users');
    }

  }

  public addUserToLocalCache(user: User): void {
    if(typeof window !== 'undefined' && localStorage){
      localStorage.setItem('user', JSON.stringify(user));
    }

  }

  public getUserFromLocalCache(): User {
    if(typeof window !== 'undefined' && localStorage){
      return JSON.parse(<string>localStorage.getItem('user'));
    }
    return new User();
  }

  public isLoggedIn(): Observable<boolean> {
    return this.http.get<User>(`${this.host}/user/me`, { withCredentials: true })
      .pipe(
        map((user: User) => {
          if (user) {
            this.addUserToLocalCache(user);
            return true;
          }
          return false;
        }),
        catchError(() => {
          this.logOut();
          return of(false);
        })
      );
  }

}
