# Decision 0025: Evidence-bound Agent answer hardening

## Status and scope

Phase 7 Step 4.1 is complete as a bounded hardening step. The functional evidence and
Citation contracts are accepted. qwen3:8b prose remains probabilistic and did not pass every
grounded-answer example; those remaining output-quality issues are backlog rather than a reason
to repeat the same live evaluation.

No new Tool, retrieval method, write capability, scheduler, notification, Multi-Agent flow,
UI, or performance optimization was added. The Agent context window remains 16,384.

## Fact, inference, and unknown policy

The system policy continues to require three sections: confirmed facts, inference, and
inspection limits. It now explicitly limits certainty to confirmed, likely based on current
evidence, possible, or cannot determine from current evidence.

- A component-level check must not become a whole-system conclusion.
- A successful JDBC connection and pgvector check do not prove that the entire database is healthy.
- A container state or exit code does not establish a root cause.
- NO_LOG_FILES means no permitted log file was found in the bounded configured scope. It is not
  a Tool failure and does not prove that no error exists.
- Git chronology and an error observed in the same inspection do not establish causation.
- A failed routed Tool is not permission to query unrelated subsystems.
- A generic Tool failure permits no invented network, permission, path, socket, or configuration cause.
- Answers must not add commands, recommendations, next steps, or lists of possible causes.

The callback boundary annotates successful Tool JSON with an evidenceAvailable flag and a
Tool-family-specific answerBoundary. Failed DTO values are still removed, and failed output now
states that the cause is unknown and omitted values must not be interpreted.

## Citation contract

The existing RagCitationValidator is reused. It now recognizes both the RAG form S1 and the
request-scoped Agent form K1-S1.

AgentToolExecution captures only citation metadata from successful searchProjectKnowledge results:
ID, redacted path, start line, and end line. Source content stays inside the bounded Tool result.
AgentChatResponse additively exposes:

- knowledgeSourceCount
- knowledgeSources
- usedSourceIds
- invalidSourceIds

An unavailable citation produces a warning. Available knowledge evidence with no citation also
produces a warning. A failed or empty Knowledge search supplies no valid citation. Runtime
Git/Docker/DB/Log facts continue to use toolsUsed and toolCalls metadata rather than Source IDs.

## Synthetic regression coverage

Automated tests cover the requested cases:

1. DB reachable=true is paired with a scope boundary that forbids an entire-DB health claim.
2. Failed Docker/DB DTO default false, zero, and empty values are removed before model use.
3. NO_LOG_FILES is explicitly a bounded observation rather than proof that errors are absent.
4. Git chronology is explicitly non-causal.
5. Successful Knowledge evidence is mapped to K1-S1 and validated.
6. Missing and invented Knowledge citations raise response warnings.
7. Knowledge source content is not copied into API citation metadata.
8. A failed Tool remains isolated while successful Tool metadata is retained.

## First live evaluation

The first Step 4.1 run completed all six selected scenarios in 4 minutes 48 seconds. Average Tool
time was 2,239.7 ms, average LLM time 43,045.3 ms, and average total time 45,328.3 ms.

| Case | Result |
|---|---|
| Overall environment | Correct observations, but inferred generic causes from an exited container: PARTIAL |
| DB cause | Kept failed DB as unknown, but invented container/configuration possibilities and advice: PARTIAL |
| Docker cause | Correct state, but invented network/internal-error explanations and a command: FAIL |
| Log plus Git | Did not assert commit causation, but called NO_LOG_FILES a failure and suggested extra checks: PARTIAL |
| Knowledge structure | Search failed because PostgreSQL was down; invented K1-S1 was detected as invalid: validator PASS, answer FAIL |
| DB success plus Log failure | Preserved DB evidence, but invented failure causes and emitted mixed Han text: PARTIAL |

This run proves that Prompt-only restraint does not guarantee grounded prose. It also proves that
invalid Knowledge citations are detected after model completion.

## Final bounded rerun and interruption

The existing local-ai-postgres container was started without recreation or volume changes and
reported healthy. A final run was started with the same six selected cases after adding structured
answerBoundary fields.

Q1, Q10, and Q11 were written to
build/reports/agent-step4-1/evaluation-selected.json. Their average Tool time was 475 ms, average
LLM time 42,739 ms, and average total time 43,287.7 ms.

- Q1: all four environment checks were correctly scoped and the answer explicitly said that
  connectivity does not guarantee whole-system health: PASS.
- Q10: only getDatabaseStatus was called and whole-DB health was not claimed, but the answer still
  suggested unrelated possible factors and more Tools: PARTIAL.
- Q11: runtime state was correct, but getRecentErrors was unnecessarily added and the answer still
  listed unsupported possible causes: PARTIAL.

Q12, the Log plus Git causality case, then waited abnormally for a qwen response. At inspection
time only Q1/Q10/Q11 existed in the JSON. The Gradle wrapper, daemon, and Test worker were alive;
no Git/Docker child Tool process was waiting. The run was interrupted, exit code 1 was expected,
and the Gradle/Test worker process tree was confirmed gone. No additional live rerun is authorized
or needed for this Step.

The final rerun did not reach the successful Knowledge case. Successful and invalid Agent Citation
paths are covered deterministically by unit tests; the earlier failed-search live case covered
post-generation invalid-ID detection.

## Automated test result

The final full Gradle run completed in 33 seconds: 135 discovered tests, 134 executed and passed,
zero failures/errors, and one opt-in live evaluation skipped by default.

The three tests that previously failed during Docker discovery were also run explicitly with the
recovered Engine and all passed:

- LocalAiWorkspaceApplicationTests
- ProjectIndexRepositoryIntegrationTest
- ProjectSemanticSearchRepositoryIntegrationTest

Their earlier failure was an unavailable Testcontainers environment, not an assertion or product
code failure. No test was deleted, skipped, or weakened.

## Decision and backlog

Recommend closing Phase 7 Step 4. The structural Tool selection, partial-failure preservation,
default-value isolation, Citation validation, and API traceability are ready.

Backlog:

1. qwen3:8b can still ignore explicit answer boundaries and emit unsupported possible causes or
   unsolicited next steps. Do not treat Agent prose as a confirmed root cause.
2. Evaluate structured answer generation, a deterministic claim policy, or a stronger model in a
   separate quality step. Do not keep tuning the same Prompt in this Step.
3. Add a bounded per-case timeout to the opt-in live evaluation harness before broad model suites.
4. Tool-result compression, fewer Tool rounds, model comparison, and context reduction remain
   performance experiments. Current accuracy-first latency is about 43 seconds average LLM time.
5. Knowledge retrieval still inherits the existing corpus ranking backlog.
