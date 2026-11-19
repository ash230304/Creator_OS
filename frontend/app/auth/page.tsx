'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { setToken, isLoggedIn } from '@/lib/auth';
import styles from './auth.module.css';

type Mode = 'login' | 'register';

export default function AuthPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isLoggedIn()) router.replace('/dashboard');
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const res = mode === 'login'
        ? await api.auth.login({ email, password })
        : await api.auth.register({ name, email, password });

      setToken(res.token, res.email);
      router.replace('/dashboard');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className={styles.wrapper}>
      {/* Left — product preview */}
      <div className={styles.left}>
        <div className={styles.leftInner}>
          <div className={styles.wordmark}>CREATOROS</div>
          <p className={styles.tagline}>Ideas in.<br />Scripts out.</p>

          <div className={styles.preview}>
            <div className={styles.previewLabel}>SAMPLE OUTPUT</div>
            <div className={styles.previewHook}>
              &ldquo;You know the algorithms, but you&rsquo;re still failing interviews.&rdquo;
            </div>
            <div className={styles.previewSection}>
              <span className={styles.previewTime}>00:00 – 00:10</span>
              <p className={styles.previewLine}>Stop memorizing LeetCode. Interviewers care about trade-offs, not syntax.</p>
            </div>
            <div className={styles.previewSection}>
              <span className={styles.previewTime}>00:10 – 00:25</span>
              <p className={styles.previewLine}>If you can&rsquo;t explain why you chose a hash map over an array, you&rsquo;re out.</p>
            </div>
            <div className={styles.previewCta}>
              CTA → &ldquo;Save this before your next interview.&rdquo;
            </div>
          </div>
        </div>
      </div>

      {/* Right — form */}
      <div className={styles.right}>
        <div className={styles.formCard}>
          <div className={styles.tabs}>
            <button
              className={mode === 'login' ? styles.tabActive : styles.tab}
              onClick={() => { setMode('login'); setError(''); }}
            >
              Sign In
            </button>
            <button
              className={mode === 'register' ? styles.tabActive : styles.tab}
              onClick={() => { setMode('register'); setError(''); }}
            >
              Create Account
            </button>
          </div>

          <form onSubmit={handleSubmit} className={styles.form}>
            {mode === 'register' && (
              <div className="field">
                <label htmlFor="name">Name</label>
                <input
                  id="name"
                  type="text"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  placeholder="Your name"
                  required
                  autoComplete="name"
                />
              </div>
            )}

            <div className="field">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
                autoComplete="email"
              />
            </div>

            <div className="field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                minLength={8}
              />
            </div>

            {error && <p className={styles.error}>{error}</p>}

            <button type="submit" className="btn-primary" disabled={loading}>
              {loading
                ? (mode === 'login' ? 'Signing in...' : 'Creating account...')
                : (mode === 'login' ? 'Sign In →' : 'Create Account →')}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
