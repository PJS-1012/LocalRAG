import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const repositoryRoot = fileURLToPath(new URL('../..', import.meta.url))
const wrapper = path.join(repositoryRoot, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')

if (!existsSync(wrapper)) {
  throw new Error(`Gradle wrapper not found: ${wrapper}`)
}

const executable = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : wrapper
const args = process.platform === 'win32'
  ? ['/d', '/s', '/c', wrapper, '-p', repositoryRoot, 'bootJar', '--no-daemon']
  : ['-p', repositoryRoot, 'bootJar', '--no-daemon']

const result = spawnSync(executable, args, {
  cwd: repositoryRoot,
  stdio: 'inherit',
  windowsHide: true,
  shell: false,
})

if (result.error) throw result.error
if (result.status !== 0) process.exit(result.status ?? 1)
