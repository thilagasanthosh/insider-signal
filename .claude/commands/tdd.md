---
description: Implement an approved insider-signal feature plan using strict red-green-refactor TDD
argument-hint: <feature-id, e.g. F-06>
---

Implement feature $ARGUMENTS following the approved plan in `docs/features/$ARGUMENTS-*.md` and the rules in `CLAUDE.md`.

1. If no approved plan exists, stop and tell me to run `/plan-feature $ARGUMENTS` first.
2. If the API changes, change `api/openapi.yaml` first.
3. Add an integration test per acceptance criterion, tagged `@Tag("$ARGUMENTS")`, and confirm it fails.
4. Work through the plan's test list one behaviour at a time:
   - **Red:** write one failing test. Run it and show that it fails for the expected reason.
   - **Green:** write the minimum code to pass it. Run the tests.
   - **Refactor:** improve the code with all tests green.
5. Never skip, delete or weaken a test to get to green. If a test seems wrong, stop and explain.
6. When the list is done, run `mvn com.spotify.fmt:fmt-maven-plugin:format` and `mvn verify`.
7. Finish with a summary: behaviours implemented, tests added, anything deferred, and any deviation from the plan.
