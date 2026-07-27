import {HttpEvent} from "@angular/common/http";

export class FileUploadStatus {
  status: string;
  percentage: number;

  constructor() {
    this.status = '',
    this.percentage = 0
  }
}
