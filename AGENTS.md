# AGENTS.md

Read docs/PRD.md before each task.

1. Section 4 (live demo flow) is the master acceptance test; don't build anything not needed for it until it passes end to end.
2. :core stays pure Kotlin: no android.* imports, no system clock; time always comes from frame timestamps.
3. Every analyzer or check change comes with a test in core/src/test.
4. Add dependencies only via gradle/libs.versions.toml, pinned to exact versions.
5. Keep it simple: no DI framework, no extra modules, no background workers, no auth.
6. Session files stay in app-private storage; never write video to the gallery.
7. Don't invent official norms or accuracy numbers; anything measured goes in README.md with how it was measured.
