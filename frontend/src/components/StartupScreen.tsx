import type { DesktopBackendStatus } from '../api/client'
import { StatusBadge } from './ui'
export default function StartupScreen({ status, retry }: { status: DesktopBackendStatus; retry: () => void }) {
  const stages = status.stages ?? ['Docker', 'PostgreSQL', 'Ollama', 'Models', 'Backend'].map(name => ({ name, state: 'WAITING', detail: '' }))
  return <main className="startup-screen"><section className="panel startup-card" aria-live="polite">
    <span className="eyebrow">LOCALRAG</span><h1>{status.state === 'STARTING' ? 'LocalRAG Starting' : 'Runtime issue detected'}</h1>
    <p>필요한 서비스를 확인하고 자동으로 준비합니다.</p>
    <div className="startup-stages">{stages.map(stage => <div key={stage.name}><strong>{stage.name}</strong><StatusBadge label={stage.state} tone={stage.state === 'READY' ? 'ok' : ['FAILED','MODEL_MISSING'].includes(stage.state) ? 'bad' : 'info'} /><small>{stage.detail}</small></div>)}</div>
    <p>{status.detail}</p>
    {status.state !== 'STARTING' && <button className="button button-primary" onClick={retry}>Retry Startup</button>}
  </section></main>
}
