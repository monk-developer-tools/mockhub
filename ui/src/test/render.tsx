import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { IconDefaults } from '../components/IconDefaults';
import { ToastProvider } from '../components/Toasts';

export function renderWithShell(ui: ReactElement, path = '/') {
  return render(
    <IconDefaults>
      <ToastProvider>
        <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{ui}</MemoryRouter>
      </ToastProvider>
    </IconDefaults>,
  );
}
