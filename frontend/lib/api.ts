import { getToken } from './auth';
import type {
  AuthResponse,
  Project,
  Script,
  GenerateScriptRequest,
  Video,
  ProcessingJob,
  Clip,
  Transcript,
} from './types';

const BASE = (process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080') + '/api/v1';
const FILES_BASE = (process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080') + '/files';

/** Build a download URL from a storage-relative path returned by the API. */
export function fileUrl(storagePath: string): string {
  const knownStoragePath = storagePath.replace(/\\/g, '/').match(/(?:^|\/)uploads\/(clips|captioned)\/(.+)$/);
  const relativePath = (knownStoragePath
    ? `${knownStoragePath[1]}/${knownStoragePath[2]}`
    : storagePath.replace(/^\.?\/?/, '')
  ).split('/').map(encodeURIComponent).join('/');
  return `${FILES_BASE}/${relativePath}`;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getToken();

  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });

  const data = await res.json();
  if (!data.success) {
    throw new Error(data.message || 'Request failed');
  }
  return data.data as T;
}

/** Multipart upload — no Content-Type header (browser sets boundary automatically) */
async function upload<T>(path: string, formData: FormData): Promise<T> {
  const token = getToken();
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
  const data = await res.json();
  if (!data.success) {
    throw new Error(data.message || 'Upload failed');
  }
  return data.data as T;
}

export const api = {
  auth: {
    register: (body: { name: string; email: string; password: string }) =>
      request<AuthResponse>('/auth/register', {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    login: (body: { email: string; password: string }) =>
      request<AuthResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify(body),
      }),
  },

  projects: {
    list: () => request<Project[]>('/projects'),
    create: (body: { name: string; platform: string; description?: string }) =>
      request<Project>('/projects', {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    delete: (id: string) =>
      request<void>(`/projects/${id}`, { method: 'DELETE' }),
  },

  scripts: {
    generate: (body: GenerateScriptRequest) =>
      request<Script>('/scripts/generate', {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    forProject: (projectId: string) =>
      request<Script[]>(`/scripts/project/${projectId}`),
  },

  videos: {
    /** Upload a video file (multipart/form-data) to a project */
    upload: (projectId: string, file: File) => {
      const form = new FormData();
      form.append('projectId', projectId);
      form.append('file', file);
      return upload<Video>('/videos', form);
    },
    forProject: (projectId: string) =>
      request<Video[]>(`/videos/project/${projectId}`),
    getById: (videoId: string) =>
      request<Video>(`/videos/${videoId}`),
    delete: (videoId: string) =>
      request<void>(`/videos/${videoId}`, { method: 'DELETE' }),
  },

  processing: {
    /** Trigger async processing pipeline for a video — returns a job */
    start: (videoId: string) =>
      request<ProcessingJob>(`/videos/${videoId}/process`, { method: 'POST' }),
    /** Poll job status */
    getJob: (jobId: string) =>
      request<ProcessingJob>(`/jobs/${jobId}`),
  },

  clips: {
    forVideo: (videoId: string) =>
      request<Clip[]>(`/videos/${videoId}/clips`),
    getById: (clipId: string) =>
      request<Clip>(`/clips/${clipId}`),
  },

  transcript: {
    forVideo: (videoId: string) =>
      request<Transcript>(`/videos/${videoId}/transcript`),
  },
};
