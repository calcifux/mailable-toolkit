# 0001. Distribution as a Library

- **Status:** Accepted
- **Date:** 2026-06-16

## Context
mailable-toolkit is one of the calcifux "navajas" — a reusable library consumed by other
applications, not an app that runs on its own. It must give consumers Spring Boot's managed
dependency versions without dragging in the app-oriented build config (the repackage plugin, an
executable jar layout, run goals) that `spring-boot-starter-parent` brings. It also needs to publish
cleanly via JitPack, whose build environment runs an older Maven and defaults to Java 8.

## Decision
Follow the same packaging convention as `auth-toolkit` and the `remote-*` libraries:

- **No `<parent>`.** The parent `pom.xml` (`com.github.calcifux:mailable-toolkit-parent`) imports
  `org.springframework.boot:spring-boot-dependencies` as a BOM (`<scope>import</scope>`) under
  `dependencyManagement` for managed versions (`spring-*`, `jakarta.mail-api`, `angus-mail`,
  `thymeleaf`, `freemarker`, `spring-data-redis`, `junit`, `assertj`, `mockito`, `slf4j`,
  `greenmail`). Pebble is pinned explicitly (`${pebble.version}`) since the BOM does not manage it.
- **Single `${revision}` + flatten.** One `<revision>` property is the version source of truth across
  the multi-module reactor (`core`, `pebble`, `thymeleaf`, `freemarker`, `spring`); the
  `flatten-maven-plugin` with `flattenMode=resolveCiFriendliesOnly` resolves `${revision}` to a
  literal at `process-resources`, so published POMs carry no variable.
- **JitPack publishing.** `groupId com.github.calcifux`, JDK 21 (`maven.compiler.release=21`),
  `jitpack.yml` forces `openjdk21` and runs `mvn clean install -DskipTests`. Build plugins
  (`maven-compiler-plugin`, `maven-surefire-plugin`, `flatten-maven-plugin`) are pinned to versions
  JitPack's older Maven accepts.
- **Basic and fast.** Deliberately no SonarCloud / JaCoCo. Tests run in CI and locally; JitPack skips
  them so consumer fetches stay quick.
- **core + spring split.** `mailable-toolkit-core` is pure Java (`jakarta.mail` + slf4j, no Spring) so
  it is usable from a CLI or job; `mailable-toolkit-spring` is the Boot starter on top.
- MIT licensed, © Carlos Guillermo Reyes Ramiro.

## Consequences
- Consumers get Boot-aligned, conflict-free versions just by importing the toolkit — no version
  pinning on their side.
- The core stays framework-agnostic and small; the same artifacts work with or without Spring.
- Published POMs are reproducible and variable-free (flatten), so JitPack resolution is reliable.
- Trade-off: not inheriting `spring-boot-starter-parent` means build/plugin config must be declared
  here instead of inherited — slightly more parent-POM boilerplate.
- Trade-off: pinning plugins to JitPack-friendly versions caps how new those plugins can be; bumping
  them needs a JitPack-compatibility check.
- Trade-off: no JaCoCo/Sonar means coverage and static-analysis gates are not enforced in this repo by
  design.
