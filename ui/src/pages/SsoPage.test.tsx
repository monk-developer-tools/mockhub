import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { SsoSettings } from '../api/types';
import { renderWithShell } from '../test/render';

const saved: Partial<SsoSettings>[] = [];
const settings: SsoSettings = {
  enabled: false, provider: 'azure', tenantId: '', clientId: '', clientSecretSet: false, clientSecretFromEnvironment: false,
  cloud: 'global', buttonLabel: 'Sign in with Microsoft', allowedDomains: [], defaultServerRole: 'none', defaultOrgId: '',
  defaultOrgRole: 'none', roleMappings: [], allowPasswordLogin: true,
  redirectUri: 'https://mock.example/__admin/mockhub/api/auth/sso/azure/callback', active: false,
};
vi.mock('../api/client', () => ({
  api: {
    sso: () => Promise.resolve(settings),
    orgs: () => Promise.resolve([{ id: 'o1', name: 'QA' }]),
    teams: () => Promise.resolve([{ id: 't1', orgId: 'o1', name: 'Checkout', slug: 'checkout' }]),
    saveSso: (body: Partial<SsoSettings>) => {
      saved.push(body);
      return Promise.resolve({ ...settings, ...body, active: true, clientSecretSet: true });
    },
    testSso: () => Promise.resolve({ ok: true, issuer: 'https://login.microsoftonline.com/t/v2.0' }),
  },
}));

const { SsoPage, ssoStatus } = await import('./SsoPage');

describe('SsoPage', () => {
  it('shows the redirect URI to paste into Entra', async () => {
    // When
    renderWithShell(<SsoPage />);

    // Then
    expect(await screen.findByText(settings.redirectUri)).toBeInTheDocument();
    expect(screen.getByText('Off')).toBeInTheDocument();
  });

  it('saves the connection, domains and group mappings', async () => {
    // Given
    renderWithShell(<SsoPage />);
    await screen.findByText(settings.redirectUri);

    // When
    await userEvent.type(screen.getByLabelText(/Directory \(tenant\) ID/), 'tenant-1');
    await userEvent.type(screen.getByLabelText('Application (client) ID'), 'client-1');
    await userEvent.type(screen.getByLabelText(/Client secret value/), 's3cret');
    await userEvent.type(screen.getByLabelText(/Allowed e-mail domains/), 'contoso.com, @fabrikam.com');
    await userEvent.click(screen.getByRole('button', { name: 'Add mapping' }));
    await userEvent.type(screen.getByLabelText('Group object ID or app role'), 'g-qa');
    await userEvent.selectOptions(screen.getByLabelText('Gives access to'), 'team:t1');
    await userEvent.click(screen.getByLabelText('Enabled'));
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));

    // Then
    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toMatchObject({
      enabled: true,
      tenantId: 'tenant-1',
      clientId: 'client-1',
      clientSecret: 's3cret',
      allowedDomains: ['contoso.com', '@fabrikam.com'],
      roleMappings: [{ claim: 'g-qa', orgId: 'o1', teamId: 't1', role: 'editor' }],
    });
    expect(await screen.findByText('On')).toBeInTheDocument();
  });

  it('reports the connection test result', async () => {
    // Given
    renderWithShell(<SsoPage />);
    await screen.findByText(settings.redirectUri);
    await userEvent.type(screen.getByLabelText(/Directory \(tenant\) ID/), 't');

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Test connection' }));

    // Then
    expect(await screen.findByText(/Tenant found/)).toBeInTheDocument();
  });

  it('labels the status', () => {
    // Then
    expect(ssoStatus({ ...settings, enabled: true }).label).toBe('Incomplete');
    expect(ssoStatus({ ...settings, enabled: true, active: true }).label).toBe('On');
  });
});
