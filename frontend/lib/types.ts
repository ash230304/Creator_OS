export interface AuthResponse {
  token: string;
  userId: string;
  name: string;
  email: string;
}

export interface Project {
  id: string;
  name: string;
  description: string | null;
  platform: 'INSTAGRAM_REEL' | 'YOUTUBE_SHORT' | 'TIKTOK' | 'TWITTER' | 'LINKEDIN';
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  updatedAt: string;
}

export interface ScriptSection {
  start: number;
  end: number;
  dialogue: string;
  visual: string;
  editing_instruction: string;
}

export interface ScriptContent {
  hook: string;
  sections: ScriptSection[];
  cta: string;
}

export interface Script {
  id: string;
  projectId: string;
  idea: string;
  platform: string;
  tone: string;
  targetDurationSeconds: number;
  content: ScriptContent;
  createdAt: string;
}

export interface GenerateScriptRequest {
  projectId: string;
  idea: string;
  platform: string;
  tone: string;
  targetDurationSeconds: number;
}

// ── Video types ─────────────────────────────────────────────────────────────

export interface Video {
  id: string;
  projectId: string;
  originalFilename: string;
  storagePath: string;
  fileSizeBytes: number | null;
  durationSeconds: number | null;
  mimeType: string | null;
  status: 'UPLOADED' | 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  updatedAt: string;
}

// ── Processing Job types ────────────────────────────────────────────────────

export type JobStatus =
  | 'QUEUED'
  | 'EXTRACTING_AUDIO'
  | 'TRANSCRIBING'
  | 'DETECTING_CLIPS'
  | 'GENERATING_CLIPS'
  | 'ADDING_CAPTIONS'
  | 'COMPLETED'
  | 'FAILED';

export interface ProcessingJob {
  jobId: string;
  videoId: string;
  status: JobStatus;
  progressPercent: number;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

// ── Clip types ──────────────────────────────────────────────────────────────

export type CaptionStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export interface Clip {
  id: string;
  videoId: string;
  startTime: number;
  endTime: number;
  durationSeconds: number;
  score: number;
  rank: number;
  storagePath: string | null;
  captionPath: string | null;
  captionStatus: CaptionStatus;
  createdAt: string;
}

// ── Transcript types ────────────────────────────────────────────────────────

export interface TranscriptSegment {
  start: number;
  end: number;
  text: string;
}

export interface Transcript {
  id: string;
  videoId: string;
  language: string | null;
  fullText: string;
  segments: TranscriptSegment[];
  createdAt: string;
}
