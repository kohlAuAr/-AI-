# Campus club platform working agreement

- This is a runnable framework, not a finished graduation project.
- Keep two independently running Spring Boot applications and one Vue frontend.
- `campus-service` owns formal campus business data. `ai-service` owns knowledge chunks and conversations.
- Demo data is public synthetic material. Do not describe it as real school data.
- Session login, CSRF, scoped recruitment/review/withdrawal, memberships, activity draft/publish, registration/cancellation and scoped participant lists are implemented. Account registration/recovery, activity editing/unpublishing/check-in, club editing, full AI document authorization, recommendation, ReAct, MCP and DAG remain planned.
- Local mode uses keyword retrieval and excerpts. Do not call it LLM generation or semantic Embedding.
- Normal developer mode connects club queries, login, recruitment, memberships, published activities, draft/publish and signup/cancellation to campus-service. Favorites and notices remain prototype-only. SAMPLE activities remain in the database but are not exposed as published events.
- Pure prototype/public builds set VITE_BUSINESS_API=false, keep browser-local mock workflows, and must not call business APIs. Keep the saved prototype archive unchanged.
- Keep LAN prototype mode separate from local API integration: vite.prototype.config.ts has no business proxy; normal developer integration pages live under /system.
- Keep model keys in environment variables or ignored local configuration. Never copy PaiSmart's credentials or database.
- Do not edit or restart the original `C:/workspace/PaiSmart` project as part of this project.
- Prefer small features across controller, service, persistence and frontend rather than empty abstractions.
- Verify backend changes with `mvn -DskipTests=false test`; frontend changes with `npm run build`.
- Verify integration with `node scripts/smoke.mjs` after starting the three processes.
- Verify the first business flow with `node scripts/check-recruitment.mjs`; after a campus restart use `node scripts/check-recruitment.mjs restored` to check the same saved record. These use synthetic demo accounts and do not delete existing records.
- Verify the activity flow with `node scripts/check-activities.mjs`; after a campus restart use `node scripts/check-activities.mjs restored`. Flow mode retains a one-seat synthetic activity; it cancels/rejoins only that named test activity, never deletes existing data. Activities are open to all student accounts; places are free text, not a venue reservation system.
- Authentication uses Spring Security Session, not JWT. Never trust frontend role switches or caller-supplied userId. Demo accounts exist only under the demo profile; do not expose this local development system publicly.
- Preserve upstream license and source attribution for adapted code.
