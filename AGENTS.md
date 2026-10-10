# AGENTS.md

Read docs/PRD.md before each task.

1. Section 4 (live demo flow) is the master acceptance test; don't build anything not needed for it until it passes end to end.
2. :core stays pure Kotlin: no android.* imports, no system clock; time always comes from frame timestamps.
3. Every analyzer or check change comes with a test in core/src/test.
4. Add dependencies only via gradle/libs.versions.toml, pinned to exact versions.
5. Keep it simple: no DI framework, no extra modules, no background workers. Exactly two auth paths exist: athlete self-registration and coach Coach-ID+PIN sign-in, both binding the device Keystore key. No passwords for athletes, no OAuth, no email/OTP, no third-party auth.
6. Session files stay in app-private storage; never write video to the gallery.
7. Don't invent official norms or accuracy numbers; anything measured goes in README.md with how it was measured.
8. The certificate is issued only when server integrity is VERIFIED and a bound coach key has signed APPROVED.
9. All signed payloads (manifest, S2 QR message, review payload) are built by shared :core code.
