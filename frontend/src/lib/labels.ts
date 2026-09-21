const labels:Record<string,string>={READY:'준비 완료',STARTING:'준비 중',WAITING:'대기 중',AVAILABLE:'사용 가능',UNAVAILABLE:'사용 불가',UNKNOWN:'확인 불가',SUCCESS:'완료',SUCCESS_WITH_WARNINGS:'확인 사항 있음',FAILED:'실패',CLEAN:'변경 없음',DIRTY:'수정된 파일 있음',CHANGED:'수정된 파일 있음',NOT_GIT_REPOSITORY:'Git 저장소 아님',PUSHED:'Push 완료',UNPUSHED:'미Push',NO_UPSTREAM:'원격 브랜치 없음',ENABLED:'사용 중',DISABLED:'사용 안 함',MODEL_MISSING:'모델 없음',PORT_IN_USE:'포트 사용 중',INSUFFICIENT_EVIDENCE:'근거 부족',NO_EVIDENCE:'근거 부족',CONTEXT_FAILED:'근거 수집 실패',LLM_FAILED:'모델 응답 실패',UNVERIFIED:'미검증',VERIFIED:'검증됨',RESOLVED:'해결됨',LOCAL_ONLY:'로컬 전용'}
export function label(value:string){return labels[value]??value.replace(/\b(AVAILABLE|UNAVAILABLE|READY|STARTING|UNKNOWN)\b/g,s=>labels[s]??s)}
export function chatWarning(value:string) {
  if(value.startsWith('Answer contains no knowledge'))return '답변에 원문 인용이 누락되었습니다. 우측 근거를 함께 확인하세요.'
  if(value.startsWith('Answer contains unavailable'))return '답변에 확인할 수 없는 인용이 있습니다. 원문과 대조가 필요합니다.'
  if(value.startsWith('Unverified initial'))return '근거 없는 초기 답변을 버리고 근거 선택을 한 번 재시도했습니다.'
  if(value.startsWith('Unverified no-tool'))return '검증할 근거가 없는 답변은 표시하지 않았습니다.'
  return label(value)
}
