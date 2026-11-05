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