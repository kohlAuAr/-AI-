# Campus club platform working agreement

- This is a runnable framework, not a finished graduation project.
- Keep two independently running Spring Boot applications and one Vue frontend.
- `campus-service` owns formal campus business data. `ai-service` owns knowledge chunks and conversations.
- Demo data is public synthetic material. Do not describe it as real school data.
- Authentication, club membership permissions, recruitment, approvals, registration, recommendation, ReAct, MCP and DAG remain planned unless implemented and verified.
- Local mode uses keyword retrieval and excerpts. Do not call it LLM generation or semantic Embedding.
- Product routes are a browser-local interactive prototype. Applications, reviews, membership, activity drafts/publishing and signup in these routes do not write the backend. Formal APIs remain planned. Preserve this disclosure.
- Keep LAN prototype mode separate from local API integration: vite.prototype.config.ts has no business proxy; normal developer integration pages live under /system.
- Keep model keys in environment variables or ignored local configuration. Never copy PaiSmart's credentials or database.
- Do not edit or restart the original `C:/workspace/PaiSmart` project as part of this project.
- Prefer small features across controller, service, persistence and frontend rather than empty abstractions.
- Verify backend changes with `mvn -DskipTests=false test`; frontend changes with `npm run build`.
- Verify integration with `node scripts/smoke.mjs` after starting the three processes.
- Preserve upstream license and source attribution for adapted code.
