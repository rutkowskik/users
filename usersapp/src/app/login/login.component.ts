import {Component, OnDestroy, OnInit} from '@angular/core';
import {NotificationService} from "../service/notification.service";
import {AuthenticationService} from "../service/authentication.service";
import {Router} from "@angular/router";
import {User} from "../model/user";
import {HttpErrorResponse, HttpResponse} from "@angular/common/http";
import {Subscription} from "rxjs";
import {NotificationType} from "../enum/notification-type.enum";
import {HeaderType} from "../enum/header-type.enum";

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit, OnDestroy {
  private subscriptions: Subscription [] = [];
  showLoading: boolean = false;

  constructor(private router: Router,
              private authenticationService: AuthenticationService,
              private notificationService: NotificationService ) {
  }


  ngOnInit(): void {
    // if(this.authenticationService.isLoggedIn()){
    //   this.router.navigateByUrl('/user/management');
    // } else {
    //   this.router.navigateByUrl('/login');
    // }
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }

  onLogin(user: User): void {
    this.showLoading = true;
    this.subscriptions.push(
      this.authenticationService.login(user).subscribe({
        next: (response: HttpResponse<User>) => {
          if (response.body != null) {
            this.authenticationService.addUserToLocalCache(response.body);
          }
          this.router.navigateByUrl('/user/management');
          this.showLoading = false;
        },
        error: (error :HttpErrorResponse)=> {
          this.sendErrorNotification(NotificationType.ERROR, error.error.message);
          this.showLoading = false;
        }
        }
      )
    )

  }

  private sendErrorNotification(errorType: NotificationType, message: string): void {
    if(message) {
      this.notificationService.notify(errorType, message);
    } else {
      this.notificationService.notify(errorType, 'An error has occurred. Please try again later.');
    }
  }
}
