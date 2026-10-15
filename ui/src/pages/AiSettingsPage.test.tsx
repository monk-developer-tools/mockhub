import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import type { AiSettings } from '../api/types';
import { renderWithShell } from '../test/render';

const saved: Partial<AiSettings>[] = [];
const settings: AiSettings = {
  enabled: false, provider: 'openrouter', baseUrl: '', model: '', temperature: 0.2, maxToolRounds: 8, apiKeySet: false, apiKeyFromEnvironment: false,
  providers: [
    { id: 'openrouter', label: 'OpenRouter', defaultBaseUrl: 'https://openrouter.ai/api/v1', defaultModel: 'openrouter/auto', requiresApiKey: true },
    { id: 'litellm', label: 'LiteLLM proxy', defaultBaseUrl: 'http://localhost:4000', defaultModel: '', requiresApiKey: false },
    { id: 'openai', label: 'Other OpenAI-compatible API', defaultBaseUrl: 'https://api.openai.com/v1', defaultModel: '', requiresApiKey: false },
  ],
};
vi.mock('../api/client', () => ({
  api: {
    aiSettings: () => Promise.resolve(settings),
    saveAiSettings: (body: Partial<AiSettings>) => {
      saved.push(body);
      return Promise.resolve({ ...settings, ...body, apiKeySet: true });
    },
    testAi: () => Promise.resolve({ ok: true, modelCount: 2, modelFound: false, models: ['gpt-4o', 'claude'] }),
  },
}));
vi.mock('../state/AppContext', () => ({ useApp: () => ({ refreshAiStatus: () => Promise.resolve() }) }));

const { AiSettingsPage } = await import('./AiSettingsPage');

describe('AiSettingsPage', () => {
  it('configures a LiteLLM proxy and saves it', async () => {
    // Given
    renderWithShell(<AiSettingsPage />);
    await userEvent.click(await screen.findByRole('radio', { name: /LiteLLM proxy/ }));

    // When
    await userEvent.type(screen.getByLabelText(/^Base URL/), 'http://litellm:4000');
    await userEvent.type(screen.getByLabelText(/API key/), 'sk-litellm');
    await userEvent.type(screen.getByLabelText(/^Model/), 'gpt-4o');
    await userEvent.click(screen.getByLabelText('Enabled'));
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));

    // Then
    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toMatchObject({ provider: 'litellm', baseUrl: 'http://litellm:4000', apiKey: 'sk-litellm', model: 'gpt-4o', enabled: true });
    expect(saved[0]).not.toHaveProperty('providers');
    expect(await screen.findByText('On')).toBeInTheDocument();
  });

  it('warns when the chosen model is not offered', async () => {
    // Given
    renderWithShell(<AiSettingsPage />);
    await screen.findByRole('radio', { name: /OpenRouter/ });

    // When
    await userEvent.click(screen.getByRole('button', { name: 'Test connection' }));

    // Then
    expect(await screen.findByText(/2 models available, but “openrouter\/auto” isn't one of them/)).toBeInTheDocument();
  });
});
