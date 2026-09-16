import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

describe('Tauri desktop security configuration', () => {
  const config = JSON.parse(readFileSync(path.join(process.cwd(), 'src-tauri/tauri.conf.json'), 'utf8'))
  const capability = JSON.parse(readFileSync(path.join(process.cwd(), 'src-tauri/capabilities/default.json'), 'utf8'))

  it('uses a fixed local API and a bounded desktop window', () => {
    expect(config.app.windows[0]).toMatchObject({ title: 'LocalRAG', minWidth: 720, resizable: true, devtools: false })
    expect(config.app.security.csp).toContain('http://127.0.0.1:18080')
    expect(config.bundle.targets).toEqual(['nsis'])
    expect(config.bundle.resources['../../build/libs/localrag-backend.jar']).toBe('backend/localrag-backend.jar')
  })

  it('does not grant shell or filesystem permissions', () => {
    expect(capability.permissions).toEqual(['core:default'])
    expect(JSON.stringify(capability)).not.toMatch(/shell|fs:/)
  })
})
