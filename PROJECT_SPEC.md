# FocusGuard – Engineering Specification

## 1. Product

FocusGuard is a personal digital self-control application.

It is NOT a parental-control product.

The application allows a user to restrict their own use of selected applications based on deterministic rules.

Supported restriction types:

- daily usage duration
- per-session duration
- daily application open count
- cooldown between launches
- time-of-day schedules
- day-of-week schedules
- temporary focus sessions
- combinations of multiple rules

Behavioral usage data is analyzed and used to recommend better restriction configurations.

AI recommendations NEVER directly modify or execute blocking settings.

An AI recommendation must always be explicitly approved by the user before a blocking rule is changed.

All actual blocking decisions must be executed by a deterministic RuleEngine.

---

# 2. Development priority

Implement Android first.

Do NOT begin iOS implementation until the core Android blocking proof of concept is reliable.

Technology:

Android:

- Kotlin
- Jetpack Compose
- Coroutines
- Flow
- Hilt
- Room
- DataStore
- UsageStatsManager
- AccessibilityService
- WorkManager
- AlarmManager

Backend:

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Gradle
- REST
- OpenAPI

Infrastructure:

- Supabase PostgreSQL
- AWS Lightsail
- Docker
- GitHub Actions

AI:

- OpenAI API
- structured JSON responses only

---

# 3. Architecture principles

The blocking feature MUST work without internet connectivity.

The following components are local-first:

- app usage monitoring
- rule evaluation
- blocking
- focus sessions
- usage event storage
- rule storage

Backend failure MUST NOT disable local blocking.

The backend is used for:

- account synchronization
- aggregated behavior statistics
- AI recommendations
- subscriptions
- device synchronization

Raw app-usage history should remain local whenever possible.

Only aggregated behavioral statistics should normally be uploaded.

---

# 4. Android architecture

Use Clean Architecture with the following high-level packages:

com.focusguard

core
common
database
datastore
network
ui

domain
model
repository
rule
usecase

data
local
remote
repository

blocking
accessibility
engine
monitor
overlay

feature
dashboard
applist
rule
focus
analytics
recommendation
settings

Do not put business rules inside Activity, ViewModel, AccessibilityService or Compose UI.

---

# 5. Rule Engine

The core domain component is RuleEngine.

It must be a pure Kotlin component with no Android dependency.

Interface concept:

RuleEngine.evaluate(
RuleContext,
List<BlockingRule>
): RuleDecision

Possible RuleDecision values:

ALLOW
WARN
BLOCK
COOLDOWN

BlockingRule interface:

evaluate(context: RuleContext): RuleResult

Implement:

DailyUsageRule
SessionUsageRule
OpenCountRule
CooldownRule
ScheduleRule
FocusSessionRule

All time durations must be internally stored as seconds.

Example:

dailyLimitSeconds = 3600
sessionLimitSeconds = 420
cooldownSeconds = 900

---

# 6. Rule priority

Blocking rules must be resolved using the following priority:

1. emergency override
2. allow list
3. active focus session
4. schedule restriction
5. daily usage limit
6. session usage limit
7. application open count
8. cooldown
9. warning
10. allow

The strongest applicable restriction becomes the final RuleDecision.

---

# 7. Rule entity

BlockingRuleEntity

Fields:

id: UUID
appIdentifier: String
type: RuleType
limitSeconds: Long?
maxOpenCount: Int?
cooldownSeconds: Long?
startTime: LocalTime?
endTime: LocalTime?
daysOfWeek: Set<DayOfWeek>
mode: RuleMode
enabled: Boolean
createdAt
updatedAt

RuleType:

DAILY_USAGE
SESSION_USAGE
OPEN_COUNT
COOLDOWN
SCHEDULE
FOCUS

RuleMode:

NORMAL
STRICT
HARD

---

# 8. Android blocking flow

Expected flow:

application foreground event

-> AppUsageMonitor

-> UsageSnapshot

-> RuleEngine

-> RuleDecision

If decision == BLOCK:

show FocusGuard blocking interface.

The AccessibilityService is an OS integration adapter only.

It must NOT contain business rule logic.

RuleEngine must be fully unit-testable without Android.

---

# 9. Required MVP behavior

Scenario A:

User selects Instagram.

User configures:

daily usage = 60 seconds.

After Instagram has been used for 60 seconds, subsequent access must be blocked.

Scenario B:

User configures:

maximum open count = 3.

The fourth attempt to open Instagram must be blocked.

Scenario C:

User configures:

cooldown = 30 seconds.

After exiting Instagram, reopening it within 30 seconds must be blocked.

These three scenarios are the initial technical acceptance criteria.

Do not add AI functionality until these three scenarios work reliably.

---

# 10. Local persistence

Use Room.

Tables:

blocking_rules
app_usage_events
app_usage_sessions
focus_sessions
daily_usage_stats
daily_behavior_stats
ai_recommendations

Use DataStore for simple application preferences.

---

# 11. Usage events

Event types:

APP_OPEN
APP_CLOSE

BLOCK_TRIGGERED
BLOCK_SCREEN_VIEWED

BYPASS_REQUESTED
BYPASS_GRANTED

FOCUS_STARTED
FOCUS_COMPLETED
FOCUS_CANCELLED

RULE_CREATED
RULE_UPDATED
RULE_DISABLED

AI_RECOMMENDATION_SHOWN
AI_RECOMMENDATION_ACCEPTED
AI_RECOMMENDATION_REJECTED

---

# 12. Behavioral aggregation

Raw events should be aggregated daily.

DailyAppBehavior should contain:

date
appIdentifier

totalUsageSeconds
openCount

averageSessionSeconds
medianSessionSeconds
longestSessionSeconds

reopenWithin1MinCount
reopenWithin5MinCount
reopenWithin15MinCount

blockedAttemptCount
bypassCount

nightUsageSeconds

Calculate:

impulseScore
focusScore
complianceScore

Do not send raw usage events to the server by default.

---

# 13. Backend

Base API:

/api/v1

Domains:

auth
user
device
rule
usage
focus
behavior
recommendation
subscription

Suggested endpoints:

GET /rules
POST /rules
GET /rules/{id}
PATCH /rules/{id}
DELETE /rules/{id}

POST /usage/daily
GET /usage/daily
GET /usage/summary

POST /focus-sessions
PATCH /focus-sessions/{id}/finish
GET /focus-sessions

POST /ai/recommendations/generate
GET /ai/recommendations

POST /ai/recommendations/{id}/accept
POST /ai/recommendations/{id}/reject

GET /dashboard/today
GET /dashboard/week

---

# 14. Backend database

Required tables:

users

devices

blocking_rules

focus_sessions

daily_usage_stats

daily_behavior_stats

ai_recommendations

recommendation_feedback

subscriptions

All schema changes must use Flyway migrations.

Never use Hibernate auto-DDL in production.

---

# 15. AI recommendation engine

The first version must NOT depend on an LLM.

Create a deterministic recommendation engine first.

Examples:

IF reopenWithin5MinRate > 0.35
THEN recommend ADD_COOLDOWN

IF nightUsageRatio > 0.40
THEN recommend ADD_SCHEDULE

IF averageSessionSeconds is high
THEN recommend SESSION_LIMIT

IF openCount is high but totalUsageSeconds is moderate
THEN recommend OPEN_COUNT_LIMIT

Later an LLM may convert behavioral features into personalized explanations.

---

# 16. AI output

All LLM output must conform to a strict JSON schema.

Example:

{
"type": "ADD_COOLDOWN",
"targetApp": "instagram",
"suggestedValueSeconds": 900,
"confidence": 0.87,
"reason": "...",
"expectedImpact": "..."
}

AI MUST NOT directly execute changes.

The UI must show:

Apply
Reject
Modify

Only Apply creates or changes BlockingRule.

---

# 17. Privacy

Treat installed application information and usage history as sensitive user information.

Follow data minimization.

Prefer:

local raw data

- server aggregated data

over centralized raw behavioral tracking.

Never use application inventory or usage behavior for advertising.

Provide clear permission disclosure before requesting usage or accessibility permissions.

---

# 18. User interface

Bottom navigation:

Home
Apps
Focus
Insights
Settings

Home:

daily screen time
focus score
blocked attempts
estimated time saved

Apps:

application icon
application name
usage today
active rules

Focus:

preset durations:
25 minutes
50 minutes
90 minutes
custom

Insights:

behavior charts
weekly statistics
AI recommendations

Settings:

permissions
privacy
sync
subscription

---

# 19. Blocking screen

Display:

application name

reason for restriction

usage statistic

time remaining

Example:

Instagram is currently restricted.

Daily opens:
10 / 10

Available again:
18 minutes

Do not shame or insult users.

---

# 20. Testing

RuleEngine requires extensive unit tests.

At minimum create tests:

dailyLimit_blocks_whenLimitExceeded

dailyLimit_allows_beforeLimit

sessionLimit_blocks_whenExceeded

openCount_blocks_whenLimitExceeded

cooldown_blocks_beforeExpiration

cooldown_allows_afterExpiration

schedule_blocks_insideRestrictedWindow

schedule_allows_outsideRestrictedWindow

focusSession_blocksSelectedApplications

allowList_overridesNormalRestrictions

combinedRules_returnMostRestrictiveDecision

Aim for very high coverage in domain/rule code.

---

# 21. Coding rules

Use immutable domain objects where reasonable.

Prefer composition over inheritance.

No business logic inside controllers.

No business logic inside Compose components.

No business logic inside AccessibilityService.

Repository interfaces belong in domain.

Implementations belong in data.

Use sealed interfaces/classes for domain result types where useful.

Use StateFlow for UI state.

Use Result-like domain errors rather than throwing arbitrary RuntimeExceptions.

Use constructor dependency injection.

Avoid premature abstraction.

Do not introduce microservices.

Do not introduce Redis until there is a proven need.

Do not introduce Kafka.

Do not introduce Python services for the MVP.

Do not build features not specified here.

---

# 22. Development workflow

Implement features vertically.

For every feature:

1. domain model
2. business logic
3. unit tests
4. local repository
5. OS adapter
6. use case
7. ViewModel
8. Compose UI
9. integration test

Do not create large amounts of placeholder architecture without implementing working behavior.

---

# 23. Initial milestone

The first implementation milestone is Android-only.

Create a working application in which:

1. the user grants required permissions
2. the user can view/select an application
3. the user can create a daily usage rule
4. application usage is detected
5. RuleEngine evaluates the rule
6. restricted application access is intercepted
7. a FocusGuard block screen appears
8. the behavior works offline
9. state survives process restart
10. RuleEngine is covered by unit tests

Do NOT start backend or AI implementation before this milestone passes.

---

# 24. Instructions to Codex

Before modifying code:

- inspect the current project
- read this specification completely
- understand existing architecture
- do not duplicate existing components

For every requested implementation:

1. explain which modules/files will change
2. implement the smallest complete vertical slice
3. add tests
4. run tests/build
5. fix compilation errors
6. report changed files
7. report remaining known limitations

Do not silently change architecture.

Do not remove tests merely to make the build pass.

Do not weaken permission/security handling.

When OS behavior is uncertain, isolate that behavior behind an interface instead of coupling it to domain logic.

This document is the source of truth for the FocusGuard project.
