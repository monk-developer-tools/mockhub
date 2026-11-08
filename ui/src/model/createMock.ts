import type { CrudRequest, ImportResult, Project, Stub } from '../api/types';
import { starterStub, type GrpcCatalog } from './grpc';
import { IMPORT_FORMAT, type StartKind } from './starts';

export interface MockApi {
  createProject: (body: Partial<Project>) => Promise<Project>;
  deleteProject: (id: string) => Promise<void>;
  importInto: (projectId: string, format: string, body: string, options?: Record<string, string>) => Promise<ImportResult>;
  uploadGrpcDescriptor: (projectId: string, name: string, file: Blob) => Promise<GrpcCatalog>;
  createStub: (projectId: string, stub: Stub) => Promise<Stub>;
  createCrud: (projectId: string, body: CrudRequest) => Promise<{ seeded: number; stub: Stub }>;
}

export type Target =
  | { mode: 'new'; name: string; slug: string; orgId: string; teamId: string; description: string }
  | { mode: 'existing'; projectId: string };

export type Content =
  | { kind: 'openapi' | 'wsdl' | 'graphql' | 'postman'; text: string; options: Record<string, string> }
  | { kind: 'grpc'; text: string; fileName: string }
  | { kind: 'crud'; request: CrudRequest }
  | { kind: 'json' | 'webhook' | 'proxy' | 'echo'; stubs: Stub[] }
  | { kind: 'ai' };

export interface Created {
  projectId: string;
  kind: StartKind;
  made: number;
  notes: string[];
}

export function protoFileName(fileName: string): string {
  const cleaned = String(fileName ?? '').replace(/[^A-Za-z0-9._-]/g, '-').replace(/^[^A-Za-z0-9]+/, '');
  return /\.proto$/i.test(cleaned) ? cleaned : 'service.proto';
}

async function fill(api: MockApi, projectId: string, content: Content): Promise<{ made: number; notes: string[] }> {
  if (content.kind === 'openapi' || content.kind === 'wsdl' || content.kind === 'graphql' || content.kind === 'postman') {
    const result = await api.importInto(projectId, IMPORT_FORMAT[content.kind] ?? content.kind, content.text, content.options);
    const skipped = (result as unknown as { skipped?: unknown }).skipped;
    return { made: result.imported, notes: Array.isArray(skipped) ? skipped.map(String) : [] };
  }
  if (content.kind === 'grpc') {
    const catalog = await api.uploadGrpcDescriptor(projectId, protoFileName(content.fileName), new Blob([content.text], { type: 'text/plain' }));
    let made = 0;
    for (const service of catalog.services) {
      for (const method of service.methods) {
        await api.createStub(projectId, starterStub(service, method));
        made++;
      }
    }
    return { made, notes: [] };
  }
  if (content.kind === 'crud') {
    const result = await api.createCrud(projectId, content.request);
    return { made: 1, notes: result.seeded ? [`${result.seeded} records saved`] : [] };
  }
  if (!('stubs' in content)) return { made: 0, notes: [] };
  for (const stub of content.stubs) await api.createStub(projectId, stub);
  return { made: content.stubs.length, notes: [] };
}

export async function createMock(api: MockApi, target: Target, content: Content): Promise<Created> {
  if (target.mode === 'existing') {
    const filled = await fill(api, target.projectId, content);
    return { projectId: target.projectId, kind: content.kind, ...filled };
  }
  const project = await api.createProject({ name: target.name, slug: target.slug, orgId: target.orgId, teamId: target.teamId, description: target.description });
  try {
    const filled = await fill(api, project.id, content);
    return { projectId: project.id, kind: content.kind, ...filled };
  } catch (e) {
    await api.deleteProject(project.id).catch(() => undefined);
    throw e;
  }
}

export function freeSlug(base: string, taken: string[]): string {
  const root = base || 'mock';
  if (!taken.includes(root)) return root;
  for (let i = 2; ; i++) {
    const candidate = `${root.slice(0, 60)}-${i}`;
    if (!taken.includes(candidate)) return candidate;
  }
}
