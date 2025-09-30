import {ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot} from '@angular/router';
import {Injectable} from "@angular/core";
import {AuthenticationService} from "../service/authentication.service";
import {NotificationService} from "../service/notification.service";
import {NotificationType} from "../enum/notification-type.enum";
import {Observable, tap} from "rxjs";


@Injectable({providedIn: 'root'})
export class AuthenticationGuard implements CanActivate {

  constructor(private authenticationService : AuthenticationService, private router: Router, private notificationService: NotificationService) {}

  //if you call backend then return Observable
  canActivate(next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
    return this.authenticationService.isLoggedIn().pipe(
      tap((loggedIn) => {
        if (!loggedIn) {
          this.router.navigate(['/login']);
          this.notificationService.notify(NotificationType.ERROR, 'You need to log in to access this page.');
        }
      })
    );
  }

}
