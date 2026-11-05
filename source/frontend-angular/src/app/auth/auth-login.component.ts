// auth-login.component.ts
import {
  Component,
  ElementRef,
  EventEmitter,
  Output,
  ViewChild,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { AuthService, LoginResponse } from './auth.service';

export type LoginState =
  | 'idle'
  | 'loading'
  | 'success'
  | 'credentials-error'
  | 'service-error'
  | 'generic-error';

/** Clasifica solo por estado HTTP; nunca muestra el cuerpo del error del servidor. */
export function loginFailureState(status: number): LoginState {
  if (status === 401) {
    return 'credentials-error';
  }

  if (status === 0 || status >= 500) {
    return 'service-error';
  }

  return 'generic-error';
}

@Component({
  selector: 'app-auth-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './auth-login.component.html',
  styleUrl: './auth-login.component.scss',
})
export class AuthLoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  @Output() readonly authenticated = new EventEmitter<LoginResponse>();
  @ViewChild('feedback') private feedback?: ElementRef<HTMLElement>;

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  state: LoginState = 'idle';
  submitting = false;

  get usernameRequiredError(): boolean {
    const control = this.form.controls.username;
    return control.touched && control.hasError('required');
  }

  get passwordRequiredError(): boolean {
    const control = this.form.controls.password;
    return control.touched && control.hasError('required');
  }

  submit(): void {
    if (this.submitting) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.state = 'loading';

    this.authService
      .login(this.form.getRawValue())
      .pipe(
        finalize(() => {
          this.submitting = false;
          // No conservar la contraseña tras una respuesta de la API.
          this.form.controls.password.setValue('');
        }),
      )
      .subscribe({
        next: (response) => {
          this.state = 'success';
          this.authenticated.emit(response);
        },
        error: (error: unknown) => {
          const status =
            error instanceof HttpErrorResponse ? error.status : 0;
          this.state = loginFailureState(status);
          this.focusFeedback();
        },
      });
  }

  private focusFeedback(): void {
    // Espera a que Angular renderice el mensaje antes de mover el foco.
    setTimeout(() => this.feedback?.nativeElement.focus());
  }
}