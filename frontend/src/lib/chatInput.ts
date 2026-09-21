import type { KeyboardEvent } from 'react'

export function submitOnEnter(event: KeyboardEvent<HTMLTextAreaElement>, disabled: boolean) {
  if (event.key !== 'Enter' || event.shiftKey || event.ctrlKey || event.altKey || event.metaKey
    || event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) return
  event.preventDefault()
  if (!disabled && !event.repeat) event.currentTarget.form?.requestSubmit()
}
