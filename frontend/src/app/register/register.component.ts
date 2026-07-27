import {Component, OnDestroy, OnInit} from '@angular/core';
import {Subscription} from "rxjs";
import {User} from "../model/user";
import {Router} from "@angular/router";
import {AuthenticationService} from "../service/authentication.service";
import {NotificationService} from "../service/notification.service";
import {HttpErrorResponse} from "@angular/common/http";
import {NotificationType} from "../enum/notification-type.enum";

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent implements OnInit, OnDestroy {

  private subscriptions: Subscription [] = [];
  showLoading: boolean = false;

  constructor(private router: Router,
              private authenticationService: AuthenticationService,
              private notificationService: NotificationService ) {
  }

  ngOnInit(): void {
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }

  onRegister(user: User): void {
    this.showLoading = true;
    this.subscriptions.push(
      this.authenticationService.register(user).subscribe({
          next: (response: User) => {
            this.sendNotification(NotificationType.SUCCESS, `A new account was created for ${response.username}.
            Check your email.`);
            this.showLoading = false;
            this.router.navigateByUrl("/login");
          },
          error: (error :HttpErrorResponse)=> {
            this.sendNotification(NotificationType.ERROR, error.error.message);
            this.showLoading = false;
          }
        }
      )
    )
  }

  private sendNotification(errorType: NotificationType, message: string): void {
    if(message) {
      this.notificationService.notify(errorType, message);
    } else {
      this.notificationService.notify(errorType, 'An error has occurred. Please try again later.');
    }
  }

}
