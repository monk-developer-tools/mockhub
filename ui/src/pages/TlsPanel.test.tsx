import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { renderWithShell } from '../test/render';

const cert = { subject: 'CN=localhost', issuer: 'CN=MockHub Test CA', notBefore: '2026-01-01T00:00:00Z', notAfter: '2126-01-01T00:00:00Z', names: ['localhost'], chainLength: 1 };
const off = { httpsEnabled: false, httpsPort: null, server: null, clientAuthorities: [] };
const on = { httpsEnabled: true, httpsPort: 8443, server: null, clientAuthorities: [] };
let current: unknown = on;
const setServerCertificate = vi.fn((_pem: string) => Promise.resolve({ ...on, server: cert }));

vi.mock('../api/client', () => ({
  api: {
    tls: () => Promise.resolve(current),
    setServerCertificate: (pem: string) => setServerCertificate(pem),
    clearServerCertificate: vi.fn(),
    setClientAuthorities: vi.fn(),
    clearClientAuthorities: vi.fn(),
  },
}));

const { TlsPanel } = await import('./TlsPanel');

describe('TlsPanel', () => {
  it('explains how to turn on the HTTPS port', async () => {
    // Given
    current = off;

    // When
    renderWithShell(<TlsPanel />);

    // Then
    expect(await screen.findByText(/The HTTPS port is off/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Use this certificate' })).not.toBeInTheDocument();
  });

  it('uploads a pasted certificate and shows it', async () => {
    // Given
    current = on;
    renderWithShell(<TlsPanel />);
    const box = await screen.findByRole('textbox', { name: 'Certificate and private key' });

    // When
    fireEvent.change(box, { target: { value: '-----BEGIN CERTIFICATE-----\nMII\n-----END CERTIFICATE-----' } });
    await userEvent.click(screen.getByRole('button', { name: 'Use this certificate' }));

    // Then
    await waitFor(() => expect(setServerCertificate).toHaveBeenCalled());
    expect(await screen.findByText('localhost')).toBeInTheDocument();
    expect(screen.getByText(/issued by MockHub Test CA/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Go back to the built-in certificate' })).toBeInTheDocument();
  });

  it('only enables saving once PEM text is pasted', async () => {
    // Given
    current = on;
    renderWithShell(<TlsPanel />);

    // When
    const box = await screen.findByRole('textbox', { name: 'CA certificates' });
    fireEvent.change(box, { target: { value: 'not a certificate' } });

    // Then
    expect(screen.getByRole('button', { name: 'Trust these CAs' })).toBeDisabled();
  });
});
