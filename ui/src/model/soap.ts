import type { HeaderPair } from '../api/types';
import { SOAP_ENVELOPES, soapContentType, soapEnvelope } from './stubForm';

export interface DetectedSoap {
  version: '1.1' | '1.2';
  action: string;
  operation: string;
  namespace: string;
}

export function detectSoap(body: string, headers: HeaderPair[] = []): DetectedSoap | null {
  const text = body?.trim();
  if (!text || !text.startsWith('<')) return null;
  const doc = new DOMParser().parseFromString(text, 'application/xml');
  if (doc.getElementsByTagName('parsererror').length) return null;
  const root = doc.documentElement;
  if (!root || root.localName !== 'Envelope') return null;
  const soapBody = Array.from(root.children).find((c) => c.localName === 'Body');
  const op = soapBody ? soapBody.firstElementChild : null;
  if (!op) return null;
  const contentType = header(headers, 'content-type') ?? '';
  const version = root.namespaceURI === SOAP_ENVELOPES['1.2'] || contentType.toLowerCase().includes('application/soap+xml') ? '1.2' : '1.1';
  const fromHeader = header(headers, 'soapaction')?.trim().replace(/^"|"$/g, '');
  const fromType = /action\s*=\s*"?([^";]+)"?/i.exec(contentType)?.[1]?.trim();
  return { version, action: fromHeader || fromType || '', operation: op.localName, namespace: op.namespaceURI ?? '' };
}

export function soapAnswer(soap: DetectedSoap): string {
  const ns = soap.namespace ? ` xmlns:ns="${soap.namespace}"` : '';
  const p = soap.namespace ? 'ns:' : '';
  const name = `${soap.operation}Response`;
  return soapEnvelope(soap.version, `<${p}${name}${ns}>\n      <${p}status>OK</${p}status>\n    </${p}${name}>`);
}

export function soapResponseType(soap: DetectedSoap): string {
  return soapContentType(soap.version);
}

function header(headers: HeaderPair[], name: string): string | undefined {
  return headers.find((h) => h.name.toLowerCase() === name)?.value;
}
