import { Component, OnDestroy, OnInit } from '@angular/core';
import { NotificationService } from "../service/notification.service";
import { AuthenticationService } from "../service/authentication.service";
import { ActivatedRoute, Router } from "@angular/router";
import { User } from "../model/user";
import { HttpErrorResponse, HttpResponse } from "@angular/common/http";
import { Subscription } from "rxjs";
import { NotificationType } from "../enum/notification-type.enum";

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit, OnDestroy {
  private subscriptions: Subscription[] = [];
  showLoading: boolean = false;
  private returnUrl: string = '/user/management';

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authenticationService: AuthenticationService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/user/management';

    console.log('LoginComponent initialized, returnUrl:', this.returnUrl);
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }

  onLogin(user: User): void {
    this.showLoading = true;
    console.log('Attempting login for user:', user.username);

    this.subscriptions.push(
      this.authenticationService.login(user).subscribe({
        next: (response: HttpResponse<User>) => {
          console.log('Login successful:', response.status);

          if (response.body != null) {
            this.authenticationService.addUserToLocalCache(response.body);
            this.notificationService.notify(
              NotificationType.SUCCESS,
              'Login successful!'
            );
          }

          this.router.navigateByUrl(this.returnUrl);
          this.showLoading = false;
        },
        error: (error: HttpErrorResponse) => {
          console.error('Login failed:', error.status, error.error);

          const message = error.error?.message || 'Invalid username or password';
          this.sendErrorNotification(NotificationType.ERROR, message);
          this.showLoading = false;
        }
      })
    );
  }

  private sendErrorNotification(errorType: NotificationType, message: string): void {
    if (message) {
      this.notificationService.notify(errorType, message);
    } else {
      this.notificationService.notify(errorType, 'An error has occurred. Please try again later.');
    }
  }
}
