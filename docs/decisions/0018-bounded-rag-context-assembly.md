# Decision 0018: Bounded RAG Context assembly

## Status

Accepted for Phase 6 Step 5. The 8,000-character limit is a RAG quality-evaluation baseline, not a final value.

## Decision

- Configure the complete formatted Context budget with `localrag.rag.context.max-characters`; default to 8,000.
- Keep one retrieved Chunk as one Source in this baseline. Do not merge adjacent Chunks yet.
- Re-sort candidates by descending similarity and assign stable citation labels `S1`, `S2`, and so on in inclusion order.
- Count the Source header, Project, path, line range, delimiters, content, and separators against the budget.
- Never truncate a Chunk. Skip a candidate that does not fit and continue evaluating later, smaller candidates.
- Keep similarity and embedding details in Source metadata only; do not put scores in the LLM-facing Context body.
- Reject the complete assembly if any returned Chunk has a different `projectId` from the searched Project.
- Treat a successful zero-result search as a successful empty Context.

## Actual verification

The existing 208-Chunk `Local_Ai_Work` index was queried with the default Query instruction, Top-K 5, threshold 0.45, and the 8,000-character Context budget.

| Query | Included | Excluded by budget | Final characters |
|---|---:|---:|---:|
| Project type discovery | 5 | 0 | 7,282 |
| Sensitive-file exclusion | 3 | 2 | 5,951 |
| Workspace boundary protection | 5 | 0 | 6,974 |
| Missing Kafka consumer configuration | 0 | 0 | 0 |

Every non-empty Context stayed within budget and omitted similarity labels. Kafka remained a successful empty Context.

## Consequences and trust boundary

The next RAG Chat step can use `sources` for citations and the formatted `context` for model input without owning retrieval or budget logic. Source content is still untrusted input: delimiters make its boundary visible but do not neutralize prompt injection. The eventual chat prompt must explicitly treat Source text as evidence, never as instructions. Source content can also resemble the delimiters, so stronger escaping or structured message separation should be evaluated with the LLM integration. Greedy whole-Chunk selection can leave unused budget; this is intentional for the baseline and should be judged with answer-quality evaluation before optimization.
