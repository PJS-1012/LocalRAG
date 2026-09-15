import type { PropsWithChildren, ReactNode } from 'react'
import type { StatusTone } from '../types'

export function StatusBadge({ label, tone = 'muted' }: { label: string; tone?: StatusTone }) {
  return <span className={`status-badge status-${tone}`}><span className="status-dot" />{label}</span>
}

export function Panel({ children, className = '' }: PropsWithChildren<{ className?: string }>) {
  return <section className={`panel ${className}`}>{children}</section>
}

export function PageHeader({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) {
  return <header className="page-header">
    <div>{eyebrow && <div className="eyebrow">{eyebrow}</div>}<h1>{title}</h1>{description && <p>{description}</p>}</div>
    {action && <div className="page-actions">{action}</div>}
  </header>
}

export function LoadingState({ label = '불러오는 중' }: { label?: string }) {
  return <div className="loading-state"><span className="spinner" /><div><strong>{label}</strong><small>로컬 환경에 따라 잠시 걸릴 수 있습니다.</small></div></div>
}

export function ErrorNotice({ message, detail }: { message: string; detail?: string }) {
  return <div className="notice notice-error"><strong>{message}</strong>{detail && <span>{detail}</span>}</div>
}

export function InfoNotice({ children }: PropsWithChildren) { return <div className="notice notice-info">{children}</div> }

export function EmptyState({ title, description }: { title: string; description: string }) {
  return <div className="empty-state"><div className="empty-mark">⌁</div><strong>{title}</strong><p>{description}</p></div>
}

export function Metric({ label, value, hint, accent }: { label: string; value: ReactNode; hint?: string; accent?: string }) {
  return <div className="metric"><span>{label}</span><strong style={accent ? { color: accent } : undefined}>{value}</strong>{hint && <small>{hint}</small>}</div>
}

export function CodeBlock({ children }: PropsWithChildren) { return <pre className="code-block"><code>{children}</code></pre> }

export function Segmented({ value, onChange, options }: { value: string; onChange: (value: string) => void; options: Array<{ value: string; label: string }> }) {
  return <div className="segmented">{options.map(option => <button key={option.value} className={value === option.value ? 'active' : ''} onClick={() => onChange(option.value)}>{option.label}</button>)}</div>
}
