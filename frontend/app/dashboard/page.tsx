'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { api } from '@/lib/api';
import { clearToken, getEmail, isLoggedIn } from '@/lib/auth';
import type { Project } from '@/lib/types';
import styles from './dashboard.module.css';

const PLATFORMS = [
  'INSTAGRAM_REEL', 'YOUTUBE_SHORT', 'TIKTOK', 'LINKEDIN', 'TWITTER',
] as const;

const PLATFORM_LABELS: Record<string, string> = {
  INSTAGRAM_REEL: 'Instagram Reel',
  YOUTUBE_SHORT:  'YouTube Short',
  TIKTOK:         'TikTok',
  LINKEDIN:       'LinkedIn',
  TWITTER:        'Twitter / X',
};

const PLATFORM_COLORS: Record<string, string> = {
  INSTAGRAM_REEL: '#e1306c',
  YOUTUBE_SHORT:  '#ff0000',
  TIKTOK:         '#69c9d0',
  LINKEDIN:       '#0077b5',
  TWITTER:        '#1da1f2',
};

function relativeTime(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(dateStr).toLocaleDateString();
}

export default function Dashboard() {
  const router = useRouter();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [newName, setNewName] = useState('');
  const [newPlatform, setNewPlatform] = useState('INSTAGRAM_REEL');
  const [creating, setCreating] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const email = getEmail();

  useEffect(() => {
    if (!isLoggedIn()) { router.replace('/auth'); return; }
    api.projects.list()
      .then(setProjects)
      .catch((err: unknown) => {
        // Only redirect to /auth on auth errors (401/403), not on network errors.
        // Redirecting on every error causes an infinite loop when backend is down.
        const msg = err instanceof Error ? err.message.toLowerCase() : '';
        if (msg.includes('401') || msg.includes('unauthorized') || msg.includes('403') || msg.includes('forbidden')) {
          clearToken();
          router.replace('/auth');
        }
        // Otherwise stay on dashboard and show empty state
      })
      .finally(() => setLoading(false));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    setCreating(true);
    try {
      const project = await api.projects.create({ name: newName.trim(), platform: newPlatform });
      setProjects(prev => [project, ...prev]);
      setNewName('');
      setNewPlatform('INSTAGRAM_REEL');
      setShowForm(false);
    } catch { /* error silently ignored */ }
    finally { setCreating(false); }
  }

  async function handleDelete(id: string) {
    try {
      await api.projects.delete(id);
      setProjects(prev => prev.filter(p => p.id !== id));
    } catch { /* error silently ignored */ }
    finally { setDeleteConfirm(null); }
  }

  function logout() {
    clearToken();
    router.replace('/auth');
  }

  return (
    <div className={styles.page}>
      {/* Nav */}
      <nav className={styles.nav}>
        <span className={styles.wordmark}>CREATOROS</span>
        <div className={styles.navRight}>
          <span className={styles.navEmail}>{email}</span>
          <span className={styles.navDivider}>|</span>
          <button className="btn-ghost" onClick={logout}>Logout</button>
        </div>
      </nav>

      <main className={styles.main}>
        {/* Header */}
        <div className={styles.header}>
          <div>
            <h1 className={styles.title}>Your Projects</h1>
            <p className={styles.subtitle}>{projects.length} project{projects.length !== 1 ? 's' : ''}</p>
          </div>
          <button className="btn-primary" onClick={() => setShowForm(v => !v)}>
            {showForm ? 'Cancel' : '+ New Project'}
          </button>
        </div>

        {/* Inline create form */}
        {showForm && (
          <form onSubmit={handleCreate} className={styles.createForm}>
            <div className="field" style={{ flex: 1 }}>
              <label htmlFor="proj-name">Project name</label>
              <input
                id="proj-name"
                type="text"
                value={newName}
                onChange={e => setNewName(e.target.value)}
                placeholder="e.g. CS Interview Tips Series"
                autoFocus
                required
              />
            </div>
            <div className="field">
              <label htmlFor="proj-platform">Platform</label>
              <select
                id="proj-platform"
                value={newPlatform}
                onChange={e => setNewPlatform(e.target.value)}
              >
                {PLATFORMS.map(p => (
                  <option key={p} value={p}>{PLATFORM_LABELS[p]}</option>
                ))}
              </select>
            </div>
            <div className={styles.createFormActions}>
              <button type="button" className="btn-secondary" onClick={() => setShowForm(false)}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={creating}>
                {creating ? 'Creating...' : 'Create'}
              </button>
            </div>
          </form>
        )}

        {/* Grid */}
        {loading ? (
          <div className={styles.grid}>
            {[1, 2, 3].map(i => (
              <div key={i} className={styles.skeletonCard}>
                <div className="skeleton" style={{ height: 20, width: '60%', marginBottom: 12 }} />
                <div className="skeleton" style={{ height: 14, width: '40%', marginBottom: 8 }} />
                <div className="skeleton" style={{ height: 14, width: '30%' }} />
              </div>
            ))}
          </div>
        ) : projects.length === 0 ? (
          <div className={styles.empty}>
            <p className={styles.emptyText}>No projects yet.</p>
            <p className={styles.emptyHint}>Create your first project to start generating scripts.</p>
          </div>
        ) : (
          <div className={styles.grid}>
            {projects.map(project => (
              <div key={project.id} className={styles.card}>
                <div className={styles.cardTop}>
                  <h2 className={styles.cardName}>{project.name}</h2>
                  <div
                    className={styles.platformDot}
                    style={{ background: PLATFORM_COLORS[project.platform] ?? '#888' }}
                    title={PLATFORM_LABELS[project.platform]}
                  />
                </div>

                <div className={styles.cardMeta}>
                  <span className={styles.metaTag}>{PLATFORM_LABELS[project.platform] ?? project.platform}</span>
                  <span
                    className={styles.statusBadge}
                    style={{ color: project.status === 'ACTIVE' ? 'var(--accent)' : 'var(--text-muted)' }}
                  >
                    {project.status}
                  </span>
                </div>

                <p className={styles.cardDate}>{relativeTime(project.createdAt)}</p>

                <div className={styles.cardActions}>
                  <Link href={`/projects/${project.id}`} className="btn-secondary">
                    Open →
                  </Link>

                  {deleteConfirm === project.id ? (
                    <div className={styles.deleteConfirm}>
                      <span className={styles.deleteQuestion}>Delete?</span>
                      <button className="btn-danger" onClick={() => handleDelete(project.id)}>Yes</button>
                      <button className="btn-ghost" onClick={() => setDeleteConfirm(null)}>No</button>
                    </div>
                  ) : (
                    <button className="btn-ghost" onClick={() => setDeleteConfirm(project.id)}>
                      Delete
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
