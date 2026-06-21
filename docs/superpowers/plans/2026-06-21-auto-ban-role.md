# Auto-Ban Role Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ban members automatically when they receive a configured restricted role.

**Architecture:** Keep role-add event handling in a dedicated global JDA listener, mirroring `AutoBanChannelListener`. Put the watched-role matching in a tiny service so the business rule is unit-tested without mocking JDA.

**Tech Stack:** Java 25, JDA 6.4.1, Gradle, JUnit Jupiter, SLF4J/Logback.

---

## File Structure

- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/services/AutoBanRoleService.java`
- Create: `src/main/java/org/camelia/studio/kiss/shot/acerola/listeners/global/AutoBanRoleListener.java`
- Create: `src/test/java/org/camelia/studio/kiss/shot/acerola/services/AutoBanRoleServiceTest.java`
- Modify: `.env.example`

## Tasks

### Task 1: Role Matching Service

- [x] Write a failing test showing that an assigned role matching `AUTO_BAN_ROLE_IDS` is detected.
- [x] Implement `AutoBanRoleService.findWatchedRoleId(Set<String> assignedRoleIds)`.
- [x] Write a test showing unrelated assigned roles do not trigger a ban.

### Task 2: JDA Listener

- [x] Create `AutoBanRoleListener` in `listeners/global/` so reflection auto-registers it.
- [x] Listen to JDA role-add events.
- [x] Ban the member when any newly assigned role is configured in `AUTO_BAN_ROLE_IDS`.
- [x] Reuse the same guard rails as `AutoBanChannelListener`: bots, owner, Discord hierarchy, and `AUTO_BAN_EXEMPT_ROLE_IDS`.
- [x] Log success and failure to SLF4J and `LOG_CHANNEL_ID`.

### Task 3: Configuration and Verification

- [x] Add `AUTO_BAN_ROLE_IDS=` to `.env.example`.
- [x] Run `./gradlew clean test compileJava`.
- [x] Review the staged diff.
- [ ] Commit and push `codex/anti-raid-auto-bans`.
