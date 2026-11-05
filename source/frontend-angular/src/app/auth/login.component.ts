// login.component.ts
import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  ViewChild,
  inject,
} from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <main>
      <h1>Iniciar sesión</h1>

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div>
          <label for="username">Usuario</label>
          <input
            #usernameInput
            id="username"
            name="username"
            type="text"
            formControlName="username"
            autocomplete="username"
            [attr.aria-invalid]="
              form.controls.username.invalid && form.controls.username.touched
            "
            [attr.aria-describedby]="
              form.controls.username.invalid && form.controls.username.touched
                ? 'username-error'
                : null
            "
          />
          <p
            *ngIf="form.controls.username.invalid && form.controls.username.touched"
            id="username-error"
          >
            Ingresa tu usuario.
          </p>
        </div>

        <div>
          <label for="password">Contraseña</label>
          <input
            #passwordInput
            id="password"
            name="password"
            type="password"
            formControlName="password"
            autocomplete="current-password"
            [attr.aria-invalid]="
              form.controls.password.invalid && form.controls.password.touched
            "
            [attr.aria-describedby]="
              form.controls.password.invalid && form.controls.password.touched
                ? 'password-error'
                : null
            "
          />
          <p
            *ngIf="form.controls.password.invalid && form.controls.password.touched"
            id="password-error"
          >
            Ingresa tu contraseña.
          </p>
        </div>

        <p *ngIf="errorMessage" role="alert">{{ errorMessage }}</p>
        <p *ngIf="successMessage" role="status" aria-live="polite">
          {{ successMessage }}
        </p>

        <button type="submit" [disabled]="submitting">
          {{ submitting ? 'Ingresando…' : 'Ingresar' }}
        </button>
      </form>
    </main>
  `,
  styles: [
    `
      :host {
        display: block;
      }

      input:focus-visible,
      button:focus-visible {
        outline: 3px solid #155eef;
        outline-offset: 2px;
      }

      [role='alert'] {
        color: #b42318;
      }
    `,
  ],
})
export class LoginComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);

  @ViewChild('usernameInput')
  private usernameInput?: ElementRef<HTMLInputElement>;

  @ViewChild('passwordInput')
  private passwordInput?: ElementRef<HTMLInputElement>;

  readonly form = this.formBuilder.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  submitting = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  submit(): void {
    if (this.submitting) {
      return;
    }

    this.errorMessage = null;
    this.successMessage = null;

    if (this.form.invalid) {
      this.form.markAllAsTouched();

      if (this.form.controls.username.invalid) {
        this.usernameInput?.nativeElement.focus();
      } else {
        this.passwordInput?.nativeElement.focus();
      }
      return;
    }

    this.submitting = true;

    // Se envían los valores tal como fueron ingresados: no se filtran ni
    // transforman caracteres que deben tratarse de forma segura en el servidor.
    this.authService
      .login(this.form.getRawValue())
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: (response) => {
          this.successMessage = `Sesión iniciada para ${response.user.username}.`;
        },
        error: (error: unknown) => {
          this.errorMessage =
            error instanceof HttpErrorResponse && error.status === 401
              ? 'Usuario o contraseña incorrectos.'
              : 'No se pudo iniciar sesión. Inténtalo nuevamente.';
        },
      });
  }
}