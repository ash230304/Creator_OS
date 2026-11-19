'use client';
import { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter, useParams } from 'next/navigation';
import Link from 'next/link';
import { api, fileUrl } from '@/lib/api';
import { clearToken, getEmail, isLoggedIn } from '@/lib/auth';
import type {
  Script,
  ScriptContent,
  Video,
  ProcessingJob,
  Clip,
  Transcript,
  TranscriptSegment,
} from '@/lib/types';
import styles from './project.module.css';

const PLATFORMS = ['INSTAGRAM_REEL', 'YOUTUBE_SHORT', 'TIKTOK', 'LINKEDIN', 'TWITTER'];
const TONES     = ['DIRECT', 'EDUCATIONAL', 'CONVERSATIONAL', 'MOTIVATIONAL', 'STORYTELLING'];
const DURATIONS = [15, 30, 45, 60];

const PLATFORM_LABELS: Record<string, string> = {
  INSTAGRAM_REEL: 'Instagram Reel',
  YOUTUBE_SHORT:  'YouTube Short',
  TIKTOK:         'TikTok',
  LINKEDIN:       'LinkedIn',
  TWITTER:        'Twitter / X',
};

const JOB_STATUS_LABELS: Record<string, string> = {
  QUEUED:           'Queued',
  EXTRACTING_AUDIO: 'Extracting audio...',
  TRANSCRIBING:     'Transcribing with Whisper...',
  DETECTING_CLIPS:  'Detecting highlights...',
  GENERATING_CLIPS: 'Cutting clips with FFmpeg...',
  ADDING_CAPTIONS:  'Burning captions...',
  COMPLETED:        'Done!',
  FAILED:           'Failed',
};

type Tab = 'script' | 'video' | 'clips';

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60).toString().padStart(2, '0');
  const s = Math.floor(seconds % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function scoreBar(score: number) {
  const pct = Math.round(score * 100);
  const color = pct >= 70 ? 'var(--accent)' : pct >= 40 ? 'var(--warning)' : 'var(--text-muted)';
  return (
    <div className={styles.scoreBar}>
      <div className={styles.scoreTrack}>
        <div className={styles.scoreFill} style={{ width: `${pct}%`, background: color }} />
      </div>
      <span className={styles.scoreLabel} style={{ color }}>{pct}%</span>
    </div>
  );
}

// ── Copy button ───────────────────────────────────────────────────────────────

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  function copy() {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }
  return (
    <button className={styles.copyBtn} onClick={copy}>
      {copied ? 'Copied ✓' : 'Copy'}
    </button>
  );
}

// ── Script view ───────────────────────────────────────────────────────────────

function ScriptView({ content }: { content: ScriptContent }) {
  return (
    <div className={styles.scriptOutput}>
      {/* Hook */}
      <div className={styles.hookCard}>
        <div className={styles.blockHeader}>
          <span className={styles.blockLabel}>HOOK</span>
          <CopyButton text={content.hook} />
        </div>
        <p className={styles.hookText}>&ldquo;{content.hook}&rdquo;</p>
      </div>

      {/* Sections */}
      <div className={styles.sections}>
        {content.sections.map((section, i) => (
          <div key={i} className={styles.sectionCard}>
            <div className={styles.sectionHeader}>
              <span className={styles.timeRange}>
                {formatTime(section.start)} – {formatTime(section.end)}
              </span>
              <span className={styles.sectionNum}>Section {i + 1}</span>
            </div>

            <div className={styles.blockGroup}>
              <div className={styles.block} data-type="dialogue">
                <div className={styles.blockHeader}>
                  <span className={styles.blockLabel}>DIALOGUE</span>
                  <CopyButton text={section.dialogue} />
                </div>
                <p className={styles.blockText}>{section.dialogue}</p>
              </div>

              <div className={styles.block} data-type="visual">
                <div className={styles.blockHeader}>
                  <span className={styles.blockLabel}>VISUAL CUE</span>
                  <CopyButton text={section.visual} />
                </div>
                <p className={styles.blockText}>{section.visual}</p>
              </div>

              <div className={styles.block} data-type="editing">
                <div className={styles.blockHeader}>
                  <span className={styles.blockLabel}>EDITING NOTE</span>
                  <CopyButton text={section.editing_instruction} />
                </div>
                <p className={styles.blockText}>{section.editing_instruction}</p>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* CTA */}
      <div className={styles.ctaCard}>
        <div className={styles.blockHeader}>
          <span className={styles.blockLabel}>CTA</span>
          <CopyButton text={content.cta} />
        </div>
        <p className={styles.ctaText}>&ldquo;{content.cta}&rdquo;</p>
      </div>
    </div>
  );
}

function SkeletonOutput() {
  return (
    <div className={styles.skeletonOutput}>
      <div className="skeleton" style={{ height: 80, borderRadius: 8, marginBottom: 16 }} />
      <div className="skeleton" style={{ height: 160, borderRadius: 8, marginBottom: 16 }} />
      <div className="skeleton" style={{ height: 160, borderRadius: 8, marginBottom: 16 }} />
      <div className="skeleton" style={{ height: 60, borderRadius: 8 }} />
    </div>
  );
}

// ── Processing progress bar ───────────────────────────────────────────────────

function JobProgress({ job }: { job: ProcessingJob }) {
  const failed = job.status === 'FAILED';
  const done   = job.status === 'COMPLETED';
  return (
    <div className={styles.jobProgress}>
      <div className={styles.jobProgressBar}>
        <div
          className={styles.jobProgressFill}
          style={{
            width: `${job.progressPercent}%`,
            background: failed ? 'var(--error)' : done ? 'var(--success)' : 'var(--accent)',
          }}
        />
      </div>
      <div className={styles.jobProgressMeta}>
        <span className={styles.jobStatusLabel} style={{ color: failed ? 'var(--error)' : done ? 'var(--success)' : 'var(--text-secondary)' }}>
          {JOB_STATUS_LABELS[job.status] ?? job.status}
        </span>
        <span className={styles.jobProgressPct}>{job.progressPercent}%</span>
      </div>
      {failed && job.errorMessage && (
        <p className={styles.jobError}>{job.errorMessage}</p>
      )}
    </div>
  );
}

// ── Main page component ───────────────────────────────────────────────────────

export default function ProjectPage() {
  const router  = useRouter();
  const params  = useParams<{ id: string }>();
  const id      = params.id;
  const email   = getEmail();

  // ── Tab state
  const [tab, setTab] = useState<Tab>('script');

  // ── Script tab state
  const [idea, setIdea]                   = useState('');
  const [platform, setPlatform]           = useState('INSTAGRAM_REEL');
  const [tone, setTone]                   = useState('DIRECT');
  const [duration, setDuration]           = useState(45);
  const [generating, setGenerating]       = useState(false);
  const [scriptError, setScriptError]     = useState('');
  const [script, setScript]               = useState<Script | null>(null);
  const [history, setHistory]             = useState<Script[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);

  // ── Video tab state
  const [videos, setVideos]               = useState<Video[]>([]);
  const [videosLoading, setVideosLoading] = useState(true);
  const [uploading, setUploading]         = useState(false);
  const [uploadError, setUploadError]     = useState('');
  const fileRef                           = useRef<HTMLInputElement>(null);

  // ── Processing state
  const [activeJob, setActiveJob]         = useState<ProcessingJob | null>(null);
  const [processingVideoId, setProcessingVideoId] = useState<string | null>(null);
  const pollRef                           = useRef<NodeJS.Timeout | null>(null);

  // ── Clips tab state
  const [clips, setClips]                 = useState<Clip[]>([]);
  const [clipsLoading, setClipsLoading]   = useState(false);
  const [transcript, setTranscript]       = useState<Transcript | null>(null);
  const [transcriptLoading, setTranscriptLoading] = useState(false);
  const [showTranscript, setShowTranscript] = useState(false);

  // ── Init ──────────────────────────────────────────────────────────────────

  useEffect(() => {
    if (!isLoggedIn()) { router.replace('/auth'); return; }

    // Load scripts
    api.scripts.forProject(id)
      .then(scripts => {
        setHistory(scripts);
        if (scripts.length > 0) setScript(scripts[0]);
      })
      .catch(() => {})
      .finally(() => setHistoryLoading(false));

    // Load videos
    api.videos.forProject(id)
      .then(vids => { setVideos(vids); })
      .catch(() => {})
      .finally(() => setVideosLoading(false));
  }, [id]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load clips when switching to the clips tab (if there are completed videos)
  useEffect(() => {
    if (tab !== 'clips') return;
    const completedVideo = videos.find(v => v.id === processingVideoId && v.status === 'COMPLETED')
      ?? [...videos].reverse().find(v => v.status === 'COMPLETED');
    if (!completedVideo) return;
    setClipsLoading(true);
    api.clips.forVideo(completedVideo.id)
      .then(setClips)
      .catch(() => {})
      .finally(() => setClipsLoading(false));
  }, [tab, videos, processingVideoId]);

  // Poll active job
  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  const startPolling = useCallback((jobId: string, videoId: string) => {
    stopPolling();
    setProcessingVideoId(videoId);
    pollRef.current = setInterval(async () => {
      try {
        const job = await api.processing.getJob(jobId);
        setActiveJob(job);
        if (job.status === 'COMPLETED' || job.status === 'FAILED') {
          stopPolling();
          // Refresh videos list
          api.videos.forProject(id).then(setVideos).catch(() => {});
          if (job.status === 'COMPLETED') {
            // Auto-switch to clips tab
            setTab('clips');
            if (videoId) {
              setClipsLoading(true);
              api.clips.forVideo(videoId)
                .then(setClips)
                .catch(() => {})
                .finally(() => setClipsLoading(false));
            }
          }
        }
      } catch {
        stopPolling();
      }
    }, 3000);
  }, [id, stopPolling]);

  useEffect(() => () => stopPolling(), [stopPolling]);

  // ── Script tab actions ────────────────────────────────────────────────────

  const generate = useCallback(async () => {
    if (!idea.trim()) return;
    setGenerating(true);
    setScriptError('');
    try {
      const result = await api.scripts.generate({
        projectId: id,
        idea: idea.trim(),
        platform,
        tone,
        targetDurationSeconds: duration,
      });
      setScript(result);
      setHistory(prev => [result, ...prev]);
    } catch (err: unknown) {
      setScriptError(err instanceof Error ? err.message : 'Generation failed');
    } finally {
      setGenerating(false);
    }
  }, [id, idea, platform, tone, duration]);

  // ── Video tab actions ─────────────────────────────────────────────────────

  const handleUpload = useCallback(async (file: File) => {
    setUploadError('');
    setUploading(true);
    try {
      const video = await api.videos.upload(id, file);
      setVideos(prev => [video, ...prev]);
    } catch (err: unknown) {
      setUploadError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
    }
  }, [id]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleUpload(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const file = e.dataTransfer.files[0];
    if (file) handleUpload(file);
  };

  const startProcessing = useCallback(async (videoId: string) => {
    try {
      const job = await api.processing.start(videoId);
      setActiveJob(job);
      startPolling(job.jobId, videoId);
      // Refresh video list to show QUEUED status
      api.videos.forProject(id).then(setVideos).catch(() => {});
    } catch (err: unknown) {
      setUploadError(err instanceof Error ? err.message : 'Failed to start processing');
    }
  }, [id, startPolling]);

  // ── Clips tab actions ─────────────────────────────────────────────────────

  const loadTranscript = useCallback(async (videoId: string) => {
    setTranscriptLoading(true);
    try {
      const t = await api.transcript.forVideo(videoId);
      setTranscript(t);
    } catch {
      setTranscript(null);
    } finally {
      setTranscriptLoading(false);
    }
  }, []);

  const loadClipsForVideo = useCallback(async (videoId: string) => {
    setClipsLoading(true);
    try {
      const c = await api.clips.forVideo(videoId);
      setClips(c);
    } catch {
      setClips([]);
    } finally {
      setClipsLoading(false);
    }
  }, []);

  // ── Misc ──────────────────────────────────────────────────────────────────

  function logout() {
    clearToken();
    router.replace('/auth');
  }

  const completedVideos = videos.filter(v => v.status === 'COMPLETED');
  const isProcessing = activeJob && activeJob.status !== 'COMPLETED' && activeJob.status !== 'FAILED';

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className={styles.page}>
      {/* Nav */}
      <nav className={styles.nav}>
        <Link href="/dashboard" className={styles.wordmark}>CREATOROS</Link>
        <div className={styles.navRight}>
          <span className={styles.navEmail}>{email}</span>
          <span className={styles.navDivider}>|</span>
          <button className="btn-ghost" onClick={logout}>Logout</button>
        </div>
      </nav>

      <div className={styles.layout}>
        {/* ── Left: Input panel ── */}
        <aside className={styles.inputPanel}>
          <div className={styles.panelHeader}>
            <Link href="/dashboard" className={styles.backLink}>← Projects</Link>
            {/* Tab nav */}
            <div className={styles.tabNav}>
              <button
                className={tab === 'script' ? styles.tabActive : styles.tabBtn}
                onClick={() => setTab('script')}
              >
                ✍️ Script
              </button>
              <button
                className={tab === 'video' ? styles.tabActive : styles.tabBtn}
                onClick={() => setTab('video')}
              >
                🎬 Video
              </button>
              <button
                className={tab === 'clips' ? styles.tabActive : styles.tabBtn}
                onClick={() => setTab('clips')}
              >
                ✂️ Clips
                {completedVideos.length > 0 && <span className={styles.tabBadge} />}
              </button>
            </div>
          </div>

          {/* ── SCRIPT TAB ── */}
          {tab === 'script' && (
            <>
              <div className={styles.inputStack}>
                <div className="field">
                  <label htmlFor="idea">What&rsquo;s your idea?</label>
                  <textarea
                    id="idea"
                    value={idea}
                    onChange={e => setIdea(e.target.value)}
                    placeholder="e.g. Why most CS students fail internship interviews despite knowing algorithms"
                    rows={5}
                  />
                </div>

                <div className="field">
                  <label htmlFor="platform">Platform</label>
                  <select id="platform" value={platform} onChange={e => setPlatform(e.target.value)}>
                    {PLATFORMS.map(p => (
                      <option key={p} value={p}>{PLATFORM_LABELS[p] ?? p}</option>
                    ))}
                  </select>
                </div>

                <div className="field">
                  <label htmlFor="tone">Tone</label>
                  <select id="tone" value={tone} onChange={e => setTone(e.target.value)}>
                    {TONES.map(t => (
                      <option key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</option>
                    ))}
                  </select>
                </div>

                <div className="field">
                  <label>Duration</label>
                  <div className={styles.segmented}>
                    {DURATIONS.map(d => (
                      <button
                        key={d}
                        className={duration === d ? styles.segActive : styles.segBtn}
                        onClick={() => setDuration(d)}
                        type="button"
                      >
                        {d}s
                      </button>
                    ))}
                  </div>
                </div>

                {scriptError && <p className={styles.error}>{scriptError}</p>}

                <button
                  className="btn-primary"
                  onClick={generate}
                  disabled={generating || !idea.trim()}
                  style={{ width: '100%', padding: '12px 16px', fontSize: 'var(--text-md)' }}
                >
                  {generating ? 'Generating...' : 'Generate Script →'}
                </button>
              </div>

              {/* History */}
              {!historyLoading && history.length > 0 && (
                <div className={styles.history}>
                  <p className={styles.historyLabel}>PREVIOUS SCRIPTS</p>
                  {history.map(s => (
                    <button
                      key={s.id}
                      className={script?.id === s.id ? styles.historyItemActive : styles.historyItem}
                      onClick={() => setScript(s)}
                    >
                      <span className={styles.historyIdea}>
                        {s.idea.length > 60 ? s.idea.slice(0, 60) + '…' : s.idea}
                      </span>
                      <span className={styles.historyDate}>
                        {new Date(s.createdAt).toLocaleDateString()}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </>
          )}

          {/* ── VIDEO TAB ── */}
          {tab === 'video' && (
            <div className={styles.videoPanel}>
              <h2 className={styles.panelTitle}>Video Upload</h2>

              {/* Upload zone */}
              <div
                className={styles.dropZone}
                onDrop={handleDrop}
                onDragOver={e => e.preventDefault()}
                onClick={() => fileRef.current?.click()}
              >
                <input
                  ref={fileRef}
                  type="file"
                  accept="video/mp4,video/quicktime,video/webm,video/x-msvideo,video/x-matroska"
                  style={{ display: 'none' }}
                  onChange={handleFileChange}
                />
                {uploading ? (
                  <div className={styles.uploadingState}>
                    <div className={styles.uploadSpinner} />
                    <p className={styles.uploadingText}>Uploading…</p>
                  </div>
                ) : (
                  <>
                    <div className={styles.dropIcon}>🎥</div>
                    <p className={styles.dropTitle}>Drop a video here</p>
                    <p className={styles.dropHint}>or click to browse — MP4, MOV, WebM, AVI, MKV · up to 500 MB</p>
                  </>
                )}
              </div>

              {uploadError && <p className={styles.error}>{uploadError}</p>}

              {/* Processing job progress */}
              {activeJob && (
                <div className={styles.jobCard}>
                  <p className={styles.jobCardLabel}>PROCESSING</p>
                  <JobProgress job={activeJob} />
                </div>
              )}

              {/* Video list */}
              {videosLoading ? (
                <div className={styles.videoList}>
                  {[1, 2].map(i => (
                    <div key={i} className={styles.videoItemSkeleton}>
                      <div className="skeleton" style={{ height: 16, width: '60%' }} />
                      <div className="skeleton" style={{ height: 12, width: '40%' }} />
                    </div>
                  ))}
                </div>
              ) : videos.length === 0 ? (
                <p className={styles.noVideos}>No videos uploaded yet.</p>
              ) : (
                <div className={styles.videoList}>
                  {videos.map(v => (
                    <div key={v.id} className={styles.videoItem}>
                      <div className={styles.videoItemTop}>
                        <span className={styles.videoName} title={v.originalFilename}>
                          {v.originalFilename.length > 32
                            ? v.originalFilename.slice(0, 30) + '…'
                            : v.originalFilename}
                        </span>
                        <span
                          className={styles.videoStatus}
                          data-status={v.status}
                        >
                          {v.status}
                        </span>
                      </div>
                      <div className={styles.videoMeta}>
                        {v.fileSizeBytes ? formatBytes(v.fileSizeBytes) : ''}
                        {v.durationSeconds ? ` · ${formatTime(v.durationSeconds)}` : ''}
                      </div>
                      {v.status === 'UPLOADED' && !isProcessing && (
                        <button
                          className="btn-primary"
                          style={{ marginTop: 8, width: '100%', padding: '8px 12px', fontSize: 'var(--text-sm)' }}
                          onClick={() => startProcessing(v.id)}
                        >
                          ▶ Process Video
                        </button>
                      )}
                      {v.status === 'COMPLETED' && (
                        <button
                          className="btn-secondary"
                          style={{ marginTop: 8, width: '100%', padding: '8px 12px', fontSize: 'var(--text-sm)' }}
                          onClick={() => { setTab('clips'); loadClipsForVideo(v.id); }}
                        >
                          View Clips →
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ── CLIPS TAB SIDEBAR ── */}
          {tab === 'clips' && (
            <div className={styles.videoPanel}>
              <h2 className={styles.panelTitle}>Clips</h2>
              {completedVideos.length === 0 ? (
                <div className={styles.clipsEmpty}>
                  <p className={styles.clipsEmptyIcon}>✂️</p>
                  <p className={styles.clipsEmptyText}>No processed videos yet.</p>
                  <p className={styles.clipsEmptyHint}>Upload and process a video to generate clips.</p>
                  <button className="btn-secondary" onClick={() => setTab('video')} style={{ marginTop: 12, width: '100%' }}>
                    Go to Video Tab →
                  </button>
                </div>
              ) : (
                <>
                  <p className={styles.historyLabel}>SELECT VIDEO</p>
                  {completedVideos.map(v => (
                    <button
                      key={v.id}
                      className={styles.historyItem}
                      onClick={() => { loadClipsForVideo(v.id); loadTranscript(v.id); }}
                    >
                      <span className={styles.historyIdea}>{v.originalFilename}</span>
                      <span className={styles.historyDate}>{new Date(v.createdAt).toLocaleDateString()}</span>
                    </button>
                  ))}

                  {/* Transcript toggle */}
                  {transcript && (
                    <div style={{ marginTop: 16 }}>
                      <button
                        className={styles.transcriptToggle}
                        onClick={() => setShowTranscript(v => !v)}
                      >
                        {showTranscript ? '▼ Hide Transcript' : '▶ Show Transcript'}
                      </button>
                      {showTranscript && (
                        <div className={styles.transcriptBox}>
                          {transcript.segments.map((seg: TranscriptSegment, i: number) => (
                            <div key={i} className={styles.transcriptSeg}>
                              <span className={styles.transcriptTime}>
                                {formatTime(seg.start)}
                              </span>
                              <span className={styles.transcriptText}>{seg.text}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </>
              )}
            </div>
          )}
        </aside>

        {/* ── Right: Output panel ── */}
        <main className={styles.outputPanel}>

          {/* ── SCRIPT OUTPUT ── */}
          {tab === 'script' && (
            generating ? (
              <SkeletonOutput />
            ) : script ? (
              <>
                <ScriptView content={script.content} />
                <div className={styles.outputActions}>
                  <button
                    className="btn-secondary"
                    onClick={generate}
                    disabled={!idea.trim()}
                  >
                    ↺ Regenerate
                  </button>
                </div>
              </>
            ) : (
              <div className={styles.emptyOutput}>
                <p className={styles.emptyTitle}>Your script will appear here</p>
                <p className={styles.emptyHint}>Fill in your idea and hit Generate</p>
                <div className={styles.fadedPreview}>
                  <div className={styles.hookCard} style={{ opacity: 0.3 }}>
                    <div className={styles.blockHeader}>
                      <span className={styles.blockLabel}>HOOK</span>
                    </div>
                    <p className={styles.hookText}>&ldquo;Your hook will appear here&rdquo;</p>
                  </div>
                </div>
              </div>
            )
          )}

          {/* ── VIDEO OUTPUT ── */}
          {tab === 'video' && (
            <div className={styles.videoOutput}>
              <div className={styles.pipelineHeader}>
                <h2 className={styles.pipelineTitle}>Processing Pipeline</h2>
                <p className={styles.pipelineSubtitle}>
                  Upload a video, click &ldquo;Process Video&rdquo;, and the pipeline will automatically
                  transcribe it, detect highlights, cut clips, and burn captions.
                </p>
              </div>

              <div className={styles.pipelineSteps}>
                {[
                  { icon: '📤', label: 'Upload', desc: 'Video file stored securely' },
                  { icon: '🔊', label: 'Extract Audio', desc: 'FFmpeg extracts WAV for Whisper' },
                  { icon: '📝', label: 'Transcribe', desc: 'Whisper generates timestamped transcript' },
                  { icon: '🔍', label: 'Detect Clips', desc: 'Heuristic algorithm scores each segment' },
                  { icon: '✂️', label: 'Cut Clips', desc: 'FFmpeg slices the best highlights' },
                  { icon: '💬', label: 'Add Captions', desc: 'SRT subtitles burned into each clip' },
                ].map((step, i) => (
                  <div key={i} className={styles.pipelineStep}>
                    <div className={styles.pipelineStepIcon}>{step.icon}</div>
                    <div>
                      <p className={styles.pipelineStepLabel}>{step.label}</p>
                      <p className={styles.pipelineStepDesc}>{step.desc}</p>
                    </div>
                    {i < 5 && <div className={styles.pipelineArrow}>→</div>}
                  </div>
                ))}
              </div>

              {activeJob && (
                <div className={styles.activeJobCard}>
                  <p className={styles.activeJobTitle}>Active Job</p>
                  <JobProgress job={activeJob} />
                </div>
              )}
            </div>
          )}

          {/* ── CLIPS OUTPUT ── */}
          {tab === 'clips' && (
            <div className={styles.clipsOutput}>
              {clipsLoading ? (
                <div className={styles.clipsGrid}>
                  {[1, 2, 3].map(i => (
                    <div key={i} className={styles.clipCardSkeleton}>
                      <div className="skeleton" style={{ height: 24, width: '40%', marginBottom: 12 }} />
                      <div className="skeleton" style={{ height: 12, width: '70%', marginBottom: 8 }} />
                      <div className="skeleton" style={{ height: 8, borderRadius: 99 }} />
                    </div>
                  ))}
                </div>
              ) : clips.length === 0 ? (
                <div className={styles.emptyOutput}>
                  <p className={styles.emptyTitle}>No clips yet</p>
                  <p className={styles.emptyHint}>
                    Select a processed video on the left to load its clips.
                  </p>
                </div>
              ) : (
                <>
                  <div className={styles.clipsHeader}>
                    <h2 className={styles.clipsTitle}>{clips.length} Clip{clips.length !== 1 ? 's' : ''} Detected</h2>
                    <p className={styles.clipsSubtitle}>Ranked by highlight score — Rank 1 is the strongest moment</p>
                  </div>
                  <div className={styles.clipsGrid}>
                    {clips.map(clip => (
                      <div key={clip.id} className={styles.clipCard}>
                        <div className={styles.clipRank}>#{clip.rank}</div>
                        <div className={styles.clipMeta}>
                          <span className={styles.clipTime}>
                            {formatTime(clip.startTime)} → {formatTime(clip.endTime)}
                          </span>
                          <span className={styles.clipDur}>
                            {clip.durationSeconds.toFixed(1)}s
                          </span>
                        </div>
                        {scoreBar(clip.score)}
                        <div className={styles.clipStatus}>
                          <span
                            className={styles.captionBadge}
                            data-status={clip.captionStatus}
                          >
                            {clip.captionStatus === 'COMPLETED'
                              ? '✓ Captions'
                              : clip.captionStatus === 'FAILED'
                              ? '✗ Caption failed'
                              : '⏳ Captions pending'}
                          </span>
                        </div>
                        {clip.captionPath ? (
                          <a
                            href={fileUrl(clip.captionPath)}
                            className="btn-primary"
                            style={{ display: 'block', textAlign: 'center', marginTop: 12, padding: '8px 12px', fontSize: 'var(--text-sm)', textDecoration: 'none', borderRadius: 'var(--radius-md)' }}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            ↓ Download (with captions)
                          </a>
                        ) : clip.storagePath ? (
                          <a
                            href={fileUrl(clip.storagePath)}
                            className="btn-secondary"
                            style={{ display: 'block', textAlign: 'center', marginTop: 12, padding: '8px 12px', fontSize: 'var(--text-sm)', textDecoration: 'none', borderRadius: 'var(--radius-md)' }}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            ↓ Download (raw clip)
                          </a>
                        ) : (
                          <p className={styles.clipPending}>Clip file not ready yet</p>
                        )}
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
