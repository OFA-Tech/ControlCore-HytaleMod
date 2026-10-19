# Contributing

Thanks for contributing!

## Guidelines
- Keep changes focused and easy to review.
- Follow the layer rules in [docs/architecture.md](docs/architecture.md). Hytale code goes in
  `infrastructure/platforms/hytale`, and another mod's code in its own platform folder.
- Expected failures return `Result`; do not throw for them.
- Add tests for new behaviour; fakes live in `src/test/java/.../fakes`.
- Keep the public API (`api` package) stable and documented.

## Local checks

```bash
./gradlew spotlessApply
./gradlew build -Pstrict   # what CI runs: tests, architecture rules, warnings as errors
```
