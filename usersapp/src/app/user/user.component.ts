import {Component, OnDestroy, OnInit} from '@angular/core';
import {BehaviorSubject, finalize, Subscription} from "rxjs";
import {User} from "../model/user";
import {UserService} from "../service/user.service";
import {NotificationService} from "../service/notification.service";
import {NotificationType} from "../enum/notification-type.enum";
import {HttpErrorResponse, HttpEvent, HttpEventType} from "@angular/common/http";
import {AuthenticationService} from "../service/authentication.service";
import {NgForm} from "@angular/forms";
import {FileUploadStatus} from "../model/file-upload-status";
import {Role} from "../enum/role.enum";

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrl: './user.component.css'
})
export class UserComponent implements OnInit, OnDestroy {

  private titleSubject = new BehaviorSubject<string>('Users');
  public titleAction$ = this.titleSubject.asObservable();
  public users: User[] = [];
  public user: User | null = null;
  public refreshing: boolean = false;
  private subscriptions: Subscription[] = [];
  selectedUser: User | undefined;
  fileName: string | undefined;
  profileImage: File | undefined;
  processingRequest: boolean = false;
  editUser: User = new User();
  private currentUsername: string = '';
  fileStatus: FileUploadStatus = new FileUploadStatus();

  constructor(
    private userService: UserService,
    private notificationService: NotificationService,
    private authenticationService: AuthenticationService
  ) {}

  ngOnInit() {
    const cachedUser = this.authenticationService.getUserFromLocalCache();

    if (!cachedUser) {
      console.error('User not found in cache, redirecting to login');
      this.authenticationService.logOut();
      return;
    }

    this.user = cachedUser;
    this.getUsers(true);
  }

  changeTitle(title: string): void {
    this.titleSubject.next(title);
  }

  public getUsers(showNotification: boolean): void {
    this.refreshing = true;
    this.subscriptions.push(
      this.userService.getUsers().subscribe({
        next: (users: User[]) => {
          this.userService.addUsersToLocalCache(users);
          this.users = users;
          this.refreshing = false;
          if (showNotification) {
            this.sendNotification(NotificationType.SUCCESS, `${users.length} user(s) loaded successfully`);
          }
        },
        error: (error: HttpErrorResponse) => {
          this.sendNotification(NotificationType.ERROR, error.message);
          this.refreshing = false;
        }
      })
    );
  }

  public onDeleteUser(userName: string): void {
    this.subscriptions.push(
      this.userService.deleteUser(userName).subscribe({
        next: () => {
          this.notificationService.notify(NotificationType.SUCCESS, 'User deleted successfully');
          this.getUsers(false);
        },
        error: (error: HttpErrorResponse) => {
          this.notificationService.notify(NotificationType.ERROR, error.error.message);
        }
      })
    );
  }

  public onResetPassword(emailFrom: NgForm): void {
    this.refreshing = true;
    const email = emailFrom.value['reset-password-email'];
    this.subscriptions.push(
      this.userService.resetPassword(email)
        .pipe(
          finalize(() => {
            emailFrom.reset();
            this.refreshing = false;
          })
        )
        .subscribe({
          next: () => {
            this.notificationService.notify(NotificationType.SUCCESS, 'The password has been reset successfully');
          },
          error: (error: HttpErrorResponse) => {
            this.notificationService.notify(NotificationType.WARNING, error.error.message);
          }
        })
    );
  }

  private sendNotification(errorType: NotificationType, message: string): void {
    if (message) {
      this.notificationService.notify(errorType, message);
    } else {
      this.notificationService.notify(errorType, 'An error has occurred. Please try again later.');
    }
  }

  onEditUser(editUser: User) {
    this.editUser = editUser;
    this.currentUsername = editUser.username;
    this.clickButton('openUserEdit');
  }

  onUpdateUser() {
    if (this.processingRequest) return;
    this.processingRequest = true;
    const formData = this.userService.createUserFormData(this.currentUsername, this.editUser, this.profileImage);
    this.subscriptions.push(
      this.userService.updatedUser(formData).subscribe({
        next: (user: User) => {
          this.clickButton('closeEditUserModalButton');
          this.getUsers(false);
          this.fileName = undefined;
          this.profileImage = undefined;
          this.sendNotification(NotificationType.SUCCESS, `${user.firstName} ${user.lastName} updated successfully`);
          this.processingRequest = false;
        },
        error: (error: HttpErrorResponse) => {
          this.sendNotification(NotificationType.ERROR, error.error.message);
          this.refreshing = false;
          this.processingRequest = false;
        }
      })
    );
  }

  onSelectUser(selectedUser: User) {
    this.selectedUser = selectedUser;
    this.clickButton('openUserInfo');
  }

  searchUsers(searchTerm: string): void {
    const results: User[] = [];
    for (const user of this.userService.getUsersFromLocalCache()) {
      if (
        user.firstName.toLowerCase().indexOf(searchTerm.toLowerCase()) !== -1 ||
        user.lastName.toLowerCase().indexOf(searchTerm.toLowerCase()) !== -1 ||
        user.userId.toLowerCase().indexOf(searchTerm.toLowerCase()) !== -1 ||
        user.username.toLowerCase().indexOf(searchTerm.toLowerCase()) !== -1
      ) {
        results.push(user);
      }
    }
    this.users = results;
    if (!searchTerm) {
      this.users = this.userService.getUsersFromLocalCache();
    }
  }

  saveNewUser() {
    this.clickButton('new-user-save');
  }

  onAddNewUser(userForm: NgForm) {
    if (this.processingRequest) return;
    this.processingRequest = true;
    const formData = this.userService.createUserFormData(null, userForm.value, this.profileImage);
    this.subscriptions.push(
      this.userService.addUser(formData).subscribe({
        next: (user: User) => {
          this.clickButton('new-user-close');
          this.getUsers(false);
          this.fileName = undefined;
          this.profileImage = undefined;
          userForm.reset();
          this.sendNotification(NotificationType.SUCCESS, `${user.firstName} ${user.lastName} added successfully`);
          this.processingRequest = false;
        },
        error: (error: HttpErrorResponse) => {
          this.sendNotification(NotificationType.ERROR, error.error.message);
          this.refreshing = false;
          this.processingRequest = false;
        }
      })
    );
  }

  onProfileImageChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      this.profileImage = input.files[0];
      this.fileName = this.profileImage.name;
    }
  }

  public get isAdmin(): boolean {
    const role = this.getUserRole();
    return role === Role.ADMIN || role === Role.SUPER_ADMIN;
  }

  public get isManager(): boolean {
    return this.isAdmin || this.getUserRole() === Role.MANAGER;
  }

  public get isAdminOrManager(): boolean {
    return this.isAdmin || this.isManager;
  }

  private getUserRole(): string {
    const user = this.authenticationService.getUserFromLocalCache();
    if (!user || !user.role) {
      console.warn('User or user role not found in cache');
      return '';
    }
    return user.role;
  }

  private clickButton(buttonId: string): void {
    const button = document.getElementById(buttonId);
    if (button) {
      button.click();
    }
  }

  updateProfileImage(): void {
    this.clickButton('profile-image-input');
  }

  onUpdateCurrentUser(user: User) {
    if (this.processingRequest) return;
    this.refreshing = true;
    this.processingRequest = true;

    const cachedUser = this.authenticationService.getUserFromLocalCache();
    if (!cachedUser) {
      this.sendNotification(NotificationType.ERROR, 'User not found in cache');
      this.processingRequest = false;
      this.refreshing = false;
      return;
    }

    this.currentUsername = cachedUser.username;
    const formData = this.userService.createUserFormData(this.currentUsername, user, this.profileImage);

    this.subscriptions.push(
      this.userService.updatedUser(formData).subscribe({
        next: (response: User) => {
          this.authenticationService.addUserToLocalCache(response);
          this.user = response;
          this.getUsers(false);
          this.fileName = undefined;
          this.profileImage = undefined;
          this.sendNotification(NotificationType.SUCCESS, `${response.firstName} ${response.lastName} updated successfully`);
          this.processingRequest = false;
          this.refreshing = false;
        },
        error: (error: HttpErrorResponse) => {
          this.sendNotification(NotificationType.ERROR, error.error.message);
          this.refreshing = false;
          this.processingRequest = false;
        }
      })
    );
  }

  onLogOut(): void {
    this.authenticationService.logOut();
    this.notificationService.notify(NotificationType.SUCCESS, "You've been logged out.");
  }

  onUpdateProfileImage(): void {
    if (!this.user) {
      this.sendNotification(NotificationType.ERROR, 'User not found');
      return;
    }

    const formData = new FormData();
    formData.append("username", this.user.username);

    if (this.profileImage) {
      formData.append("image", this.profileImage);
      this.subscriptions.push(
        this.userService.updateProfileImage(formData).subscribe({
          next: (httpEvent: HttpEvent<any>) => {
            this.reportUploadProgress(httpEvent);
          },
          error: (response: HttpErrorResponse) => {
            this.sendNotification(NotificationType.ERROR, response.error.message);
            this.fileStatus.status = 'done';
          }
        })
      );
    }
  }

  private reportUploadProgress(httpEvent: HttpEvent<any>) {
    switch (httpEvent.type) {
      case HttpEventType.UploadProgress:
        if (httpEvent.total) {
          this.fileStatus.percentage = Math.round(100 * httpEvent.loaded / httpEvent.total);
        }
        this.fileStatus.status = 'progress';
        break;
      case HttpEventType.Response:
        if (httpEvent.status === 200) {
          if (this.user) {
            this.user.profileImageUrl = `${httpEvent.body.profileImageUrl}?time=${new Date().getMilliseconds()}`;
          }
          this.notificationService.notify(NotificationType.SUCCESS, `${httpEvent.body.firstName}'s profile image uploaded successfully`);
          this.fileStatus.status = 'done';
          break;
        } else {
          this.notificationService.notify(NotificationType.ERROR, `Unable to upload image. Please try again`);
          break;
        }
      default:
        console.log('Finished all processes');
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => subscription.unsubscribe());
  }
}
