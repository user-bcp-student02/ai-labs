```ts
// auth.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * La forma de la respuesta exitosa no está especificada aquí.
 * El componente la entrega al contenedor sin guardar ni interpretar tokens.
 */
export type LoginResponse = unknown;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/auth/login', credentials);
  }
}
```

```ts
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
```

```html
<!-- auth-login.component.html -->
<form
  [formGroup]="form"
  (ngSubmit)="submit()"
  [attr.aria-busy]="submitting"
  novalidate
>
  <div class="field">
    <label for="login-username">Usuario</label>
    <input
      id="login-username"
      type="text"
      formControlName="username"
      autocomplete="username"
      required
      [attr.aria-invalid]="usernameRequiredError"
      [attr.aria-describedby]="usernameRequiredError ? 'username-error' : null"
    />
    <p
      *ngIf="usernameRequiredError"
      id="username-error"
      class="field-error"
    >
      Ingresa tu usuario.
    </p>
  </div>

  <div class="field">
    <label for="login-password">Contraseña</label>
    <input
      id="login-password"
      type="password"
      formControlName="password"
      autocomplete="current-password"
      required
      [attr.aria-invalid]="passwordRequiredError"
      [attr.aria-describedby]="passwordRequiredError ? 'password-error' : null"
    />
    <p
      *ngIf="passwordRequiredError"
      id="password-error"
      class="field-error"
    >
      Ingresa tu contraseña.
    </p>
  </div>

  <button type="submit" [disabled]="submitting || form.invalid">
    {{ submitting ? 'Ingresando…' : 'Ingresar' }}
  </button>

  <p *ngIf="state === 'loading'" role="status" aria-live="polite">
    Estamos verificando tus datos.
  </p>

  <p *ngIf="state === 'success'" role="status" aria-live="polite">
    Inicio de sesión correcto.
  </p>

  <div
    *ngIf="
      state === 'credentials-error' ||
      state === 'service-error' ||
      state === 'generic-error'
    "
    #feedback
    tabindex="-1"
    role="alert"
    aria-live="assertive"
    class="feedback-error"
  >
    <ng-container [ngSwitch]="state">
      <span *ngSwitchCase="'credentials-error'">
        Usuario o contraseña incorrectos.
      </span>
      <span *ngSwitchCase="'service-error'">
        No podemos completar el inicio de sesión ahora. Inténtalo de nuevo más
        tarde.
      </span>
      <span *ngSwitchDefault>
        No se pudo completar el inicio de sesión. Inténtalo de nuevo.
      </span>
    </ng-container>
  </div>
</form>
```

```scss
// auth-login.component.scss
:host {
  display: block;
}

.field {
  margin-block-end: 1rem;
}

label {
  display: block;
  margin-block-end: 0.35rem;
}

input {
  box-sizing: border-box;
  width: 100%;
  min-height: 2.75rem;
  padding: 0.5rem;
}

button {
  min-height: 2.75rem;
  padding-inline: 1rem;
}

input:focus-visible,
button:focus-visible,
.feedback-error:focus-visible {
  outline: 3px solid #1457c5;
  outline-offset: 2px;
}

.field-error,
.feedback-error {
  color: #a32020;
}

.feedback-error {
  margin-block-start: 1rem;
}
```

```ts
// auth.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { loginFailureState } from './auth-login.component';

describe('AuthService', () => {
  let service: AuthService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });

    service = TestBed.inject(AuthService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('envía las credenciales a POST /auth/login y conserva la respuesta', () => {
    const credentials = {
      username: 'test-user',
      password: 'test-password',
    };
    const response = { result: 'test-success' };

    service.login(credentials).subscribe((actual) => {
      expect(actual).toEqual(response);
    });

    const request = httpTestingController.expectOne('/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(credentials);
    request.flush(response);
  });

  it('propaga el 401 sin convertirlo en un error de servicio', () => {
    let receivedStatus: number | undefined;

    service.login({ username: 'test-user', password: 'wrong-password' })
      .subscribe({
        error: (error) => {
          receivedStatus = error.status;
        },
      });

    httpTestingController
      .expectOne('/auth/login')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(receivedStatus).toBe(401);
    expect(loginFailureState(receivedStatus)).toBe('credentials-error');
  });

  it('propaga un fallo de servicio y lo distingue de credenciales inválidas', () => {
    let receivedStatus: number | undefined;

    service.login({ username: 'test-user', password: 'test-password' })
      .subscribe({
        error: (error) => {
          receivedStatus = error.status;
        },
      });

    httpTestingController
      .expectOne('/auth/login')
      .flush({}, { status: 503, statusText: 'Service Unavailable' });

    expect(receivedStatus).toBe(503);
    expect(loginFailureState(receivedStatus)).toBe('service-error');
  });

  it('clasifica una falla de red como error de servicio', () => {
    expect(loginFailureState(0)).toBe('service-error');
  });
});
```

# frontend_spec.md

## Alcance

Recorrido de inicio de sesión para `POST /auth/login`, con distinción visual entre credenciales rechazadas y errores de servicio. El frontend no interpreta el cuerpo de los errores ni muestra mensajes recibidos del servidor.

## Contrato usado

- **Método y ruta:** `POST /auth/login`.
- **Solicitud asumida por este ejemplo:** `{ "username": "...", "password": "..." }`. Confirmar los nombres y el formato con el contrato real del endpoint antes de integrar.
- **Credenciales incorrectas:** `401`; mostrar un mensaje genérico, sin distinguir si el usuario existe.
- **Fallo de persistencia:** el backend debe responder como error de servicio, preferentemente `503` según el contrato aprobado. El frontend también trata otros `5xx` y fallos de red como error de servicio.
- **Éxito:** la respuesta se entrega al contenedor mediante `authenticated`; no se asumen campos de token ni se guarda la respuesta. El contenedor debe integrarla con el mecanismo de sesión aprobado.

## Campos, validaciones y acciones

- **Usuario:** obligatorio; etiqueta visible y `autocomplete="username"`.
- **Contraseña:** obligatoria; se envía sin modificar y usa `autocomplete="current-password"`.
- No se envía el formulario si falta un campo.
- Al recibir respuesta, se limpia el campo de contraseña.
- El botón se deshabilita durante el envío y se ignoran nuevos intentos mientras hay una solicitud activa.
- El formulario puede enviarse con teclado mediante Enter.

## Estados y mensajes

| Estado | Presentación |
|---|---|
| Inicial | Campos vacíos, botón disponible cuando el formulario es válido. |
| Cargando | Botón deshabilitado y mensaje “Estamos verificando tus datos.” |
| Éxito | Mensaje “Inicio de sesión correcto.” y emisión del evento `authenticated`. |
| `401` | “Usuario o contraseña incorrectos.” |
| Red o `5xx` | “No podemos completar el inicio de sesión ahora. Inténtalo de nuevo más tarde.” |
| Otro error | Mensaje genérico; no se muestra el cuerpo de la respuesta. |

## Accesibilidad

- Etiquetas asociadas a sus campos, errores de validación relacionados con `aria-describedby` y estado inválido reflejado con `aria-invalid`.
- Estados anunciados con regiones `status`/`alert`; tras un error, el foco pasa al mensaje.
- Estilos `:focus-visible` en controles y mensaje enfocable. No se depende únicamente del color para indicar el estado.

## Comprobación

Ejecutar las pruebas unitarias con el comando de test configurado en el proyecto (por ejemplo, `ng test`). Las pruebas incluidas verifican el método, la ruta y el cuerpo de login, y que un `401` no se confunda con un `503`.

Comprobación manual adicional:

1. Enviar el formulario vacío y verificar las validaciones, sin petición.
2. Enviar credenciales incorrectas y verificar el mensaje genérico de rechazo.
3. Simular `503` o desconectar la API y verificar el mensaje de servicio, sin detalles técnicos.
4. Mientras la petición está pendiente, intentar enviar varias veces: debe existir una sola petición.
5. Revisar navegación por teclado, foco visible y anuncios del lector de pantalla.

La distinción depende de que el backend preserve el `401` para credenciales incorrectas y devuelva un estado `5xx` para fallos de persistencia.