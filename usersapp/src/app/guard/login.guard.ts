import {Injectable} from "@angular/core";
import {CanActivate, Router} from "@angular/router";
import {AuthenticationService} from "../service/authentication.service";
import {map, Observable} from "rxjs";

@Injectable({ providedIn: 'root' })
export class LoginGuard implements CanActivate {
  constructor(private auth: AuthenticationService, private router: Router) {}

  canActivate(): Observable<boolean> {
    return this.auth.isLoggedIn().pipe(
      map(loggedIn => {
        if (loggedIn) {
          this.router.navigate(['/user/management']);
          return false;
        }
        return true;
      })
    );
  }
}
