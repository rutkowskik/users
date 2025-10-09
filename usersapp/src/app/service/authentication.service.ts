import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { environment } from "../../environments/environment";
import { catchError, map, Observable, of, tap } from "rxjs";
import { User } from '../model/user';
import { Router } from "@angular/router";

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {

  public host = environment.apiUrl;
  private loggedInUsername: string | null = null;
  private checkingAuth = false;

  constructor(private http: HttpClient, private router: Router) {
    const cachedUser = this.getUserFromLocalCache();
    if (cachedUser?.username) {
      this.loggedInUsername = cachedUser.username;
    }
  }

  public login(user: User): Observable<HttpResponse<User>> {
    return this.http.post<User>(
      `${this.host}/user/login`,
      user,
      { observe: 'response', withCredentials: true }
    ).pipe(
      tap((response) => {
        if (response.body) {
          this.addUserToLocalCache(response.body);
        }
      })
    );
  }

  public register(user: User): Observable<User> {
    return this.http.post<User>(`${this.host}/user/register`, user);
  }

  public logOut(): void {
    this.http.post(`${this.host}/user/logout`, {}, { withCredentials: true })
      .subscribe({
        next: () => {
          this.clearLocalCache();
          this.router.navigate(['/login']);
        },
        error: () => {
          this.clearLocalCache();
          this.router.navigate(['/login']);
        }
      });
  }

  public addUserToLocalCache(user: User): void {
    this.loggedInUsername = user.username;
    if (typeof window !== 'undefined' && localStorage) {
      localStorage.setItem('user', JSON.stringify(user));
    }
  }

  public getUserFromLocalCache(): User | null {
    if (typeof window !== 'undefined' && localStorage) {
      const userJson = localStorage.getItem('user');
      if (userJson) {
        try {
          return JSON.parse(userJson);
        } catch (e) {
          return null;
        }
      }
    }
    return null;
  }

  private clearLocalCache(): void {
    if (typeof window !== 'undefined' && localStorage) {
      localStorage.removeItem('user');
      localStorage.removeItem('users');
    }
    this.loggedInUsername = null;
  }

  public isLoggedIn(): Observable<boolean> {
    if (this.loggedInUsername) {
      return of(true);
    }
    if (this.checkingAuth) {
      return of(false);
    }

    this.checkingAuth = true;

    return this.http.get<User>(`${this.host}/user/me`, { withCredentials: true })
      .pipe(
        map((user: User) => {
          this.checkingAuth = false;
          if (user && user.username) {
            this.addUserToLocalCache(user);
            return true;
          }
          return false;
        }),
        catchError(() => {
          this.checkingAuth = false;
          this.clearLocalCache();

          return of(false);
        })
      );
  }

  public checkAuthStatus(): Observable<boolean> {
    this.loggedInUsername = null;
    return this.isLoggedIn();
  }
}
