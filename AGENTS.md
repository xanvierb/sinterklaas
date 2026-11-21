# AGENTS.md — Sinterklaas

## Mission and working agreement

Build a production-minded, small-scale, Dutch-language Sinterklaas web application in the **existing repository**. The feature list in this document is authoritative. Implement working end-to-end features, not placeholder buttons or mock data. Keep solutions pragmatic, secure, maintainable, and consistent with the current project.

**Before making changes:** inspect the actual repository, `pom.xml`, existing source code, configuration and migrations. Do not overwrite working code blindly. Identify missing pieces, implement them in small verifiable steps, and run the relevant Maven tests after every substantial change. If a detail is unspecified, choose the least complex secure option, document the decision and proceed. Do not introduce unchecked features from the out-of-scope list.

**User-visible language:** Dutch (nl-NL). Code, Java class names, database identifiers, comments and commit messages may be English; keep terminology consistent. Code must be understandable without an external AI service.

## Fixed technical choices

- Java **25**, Spring Boot **4.1.2**, **Maven** only. Preserve `spring-boot-starter-parent` and the existing `pom.xml` structure.
- One deployable Spring Boot monolith. **Spring MVC + Thymeleaf** for server-rendered pages, **HTMX 2.x** for progressive enhancements, **Bootstrap 5.3** for responsive layout and themeable components.
- Spring Security, session-based username/password login and role authorization. No JWT, OAuth2, external identity service, SPA frontend, Vue, React, or separate Node.js backend.
- Spring Data JPA/Hibernate, PostgreSQL, Flyway migrations. Schema changes through Flyway only; never rely on Hibernate `ddl-auto=update` in deployed environments.
- Existing project dependencies include webmvc, Thymeleaf, Security, JPA, PostgreSQL, Flyway, Actuator, Lombok, and targeted test starters. Use them rather than duplicating or replacing them.
- Add only necessary dependencies: Bean Validation (`spring-boot-starter-validation`), a maintained HTML parser such as **jsoup** for public product metadata, and Testcontainers PostgreSQL/JUnit if needed for realistic integration tests. Verify coordinates/versions in the actual project before editing the POM. Keep dependency count modest.
- Serve version-pinned HTMX and Bootstrap assets locally in `src/main/resources/static/` (or via a reproducible build); avoid runtime reliance on public CDNs. Favor plain JavaScript only for enhancements that HTMX cannot express cleanly.
- Never use Gradle. Never add an AI/LLM dependency, unnecessary messaging infrastructure, Kubernetes, or microservices.

## Scope: baseline requirements (requested by the owner)

All of the following are mandatory:

1. Users log in with a **username and password**.
2. Users have profiles including **display name, picture and basic profile details**.
3. An **ADMIN** can create and modify users and access/manage all application data, including gifts and reservations; the UI should warn that admin access can reveal surprises.
4. Users create, edit and remove items on **their own wish lists**.
5. Authenticated users can **browse other participants' wish lists**.
6. Users can **reserve / unreserve** gifts from other participants' lists.
7. Gift reservations and purchase details must **not be visible to the gift recipient**, including via APIs, HTML, HTMX fragments, counts, HTML attributes or URLs revealing reservation state.
8. Each user has a **private overview of gifts they have reserved** for others.
9. Prevent **double reservations** of the same item, even under concurrent HTTP requests.

## Scope: owner-selected additional features

The numbers below correspond to the owner's numbered selection; implement every item:

| No. | Feature | Required behavior |
|---|---|---|
| 1 | Change own password | Verify old password; confirm and validate new password; securely update its hash. |
| 4 | Invite new users | Admin creates a single-use, expiring invitation link that can be copied and handed to the invitee; invitee sets username/password and profile. No public registration. Email delivery is not required. |
| 5 | Deactivate accounts | Admin deactivates/reactivates accounts; disabled accounts cannot log in; preserve references and history. |
| 7 | Product URLs | A wish may contain an external webshop URL; render a safe clickable link. |
| 8 | Fetch product metadata | On explicit user action, attempt to retrieve product name, description, image and price from the submitted URL (prefer Open Graph / structured metadata, then safe fallbacks). Always allow manual editing when a site blocks scraping. |
| 9 | Upload wish images | User can upload or replace an image for their wish. |
| 10 | Wish priority | Set and display a simple priority such as LOW, NORMAL, HIGH. |
| 11 | Price indication | Store an estimated price (decimal and currency EUR). No promises that scraped prices are current. |
| 12 | Variants | Store desired color, size, model or other product-specific preferences. |
| 13 | Wish details | Description and notes supplied by the owner. |
| 16 | Gift workflow status | For each reservation: RESERVED, ORDERED, RECEIVED, WRAPPED, GIVEN; authorized buyers can update status, never the recipient. |
| 21 | Buy together | Allow several purchasers to participate in **one** reserved wish, with a lead purchaser and co-buyers. Participants see joint-purchase details; nonparticipants only see the item is unavailable (if they are not the recipient). No payment processing or transaction ledger. |
| 28 | Group budget agreement | An admin can set a **shared, informational budget agreement** for the default Sinterklaas household/group, shown to participants. This is not personal expense tracking. |
| 34 | Countdown | Show days remaining until the configured Sinterklaas date (default **5 December** of the relevant current year); support a non-negative 'today / already passed' display. |
| 35 | Gift counts per recipient | Buyers/admin can see how many gifts each participant is expected to receive based on active reservations. A recipient must not be shown their own reserved-gift count or other spoiler data. Respect joint gifts as a single gift. |
| 36 | Responsive interface | Fully usable on phones, tablets and desktop, including navigation, forms and reservation controls. |
| 39 | Personal dashboard | Show own purchase responsibilities, progress through workflow statuses, countdown and useful next actions; never leak incoming surprises. |
| 41 | Print | Print-friendly personal shopping overview and permitted wish-list views via dedicated print CSS; preserve authorization and secrecy. No PDF engine required. |
| 42 | Theming | Theme architecture, not only a dark-mode switch. Support SYSTEM/LIGHT/DARK color modes and interchangeable visual palettes (at least a Sinterklaas default and one alternative), stored per user. Use design tokens/CSS variables; support future themes without changing templates. |
| 43 | HTMX | Interactive add/edit/reserve/status flows with partial HTML updates, progressive enhancement and full-page fallbacks. |
| 44 | Frontend styling | Prefer **Bootstrap 5.3** for a familiar, accessible, extensible UI; other UI libraries only if there is a demonstrable technical reason and no needless new build tool. |

## Explicitly out of scope

Do **not** add any of the following unless the owner explicitly changes scope:

- Self-registration, password-reset emails, mailing lists, email notifications, or mandatory SMTP.
- Multi-household/group management UI, group invites, group admins, configurable annual participant enrollment or event planning. A single seeded/default household/group is sufficient to support item 28; design the data model so it can be extended later.
- Child logins and purchasing on behalf of a child. Parents may create and co-manage child profiles, wishes and recipient-side chats.
- Drawing lots, exclusions, poems, physical surprises, or gift exchange scheduling.
- Multiple independent wish lists, wish-list history/archive UI, birthday/Christmas products.
- Personal spending logs, individual budgets, receipts, payment collection, online splitting/settlement of money.
- Gifts unrelated to a wish-list item.
- PWA installability, web-push, export-to-PDF, analytics dashboards, public API, OAuth, mobile apps, or social login.

Necessary infrastructure (CSRF, validation, authorization, audit timestamps, safe image upload, tests) is in scope even when not listed as a user-visible feature.

## Domain and data modeling guidance

Start with a simple relational domain; adjust naming after inspecting the repository:

- `User`: immutable ID, unique normalized username, BCrypt/Argon2 password hash, enabled flag, display name, optional profile details, avatar reference, role (`USER`/`ADMIN`), preferred color mode, preferred palette, audit timestamps. Disabling must not cascade-delete reservations.
- `Invitation`: ID, hashed random token (never raw token at rest), expiry, single-use redemption timestamp, created-by admin, optional reserved username. Token must be unguessable, short-lived, invalid after redemption, and consumed atomically.
- `Household` or `GiftGroup`: **one default instance** for now, with textual budget agreement (optionally structured amount and EUR currency); users are participants in that default instance. Do not build management screens for multiple groups.
- `Wish`: ID, owner, title, owner description/notes, product URL, optional metadata fields, estimated price as `BigDecimal`, priority, variants, image reference, created/updated timestamps. Use one effective wish list per user, no `Wishlist` entity unless it provides real value.
- `GiftReservation`: ID, unique wish ID (one active reservation per wish), lead purchaser, status, optional buyer-only notes, timestamps, optimistic-lock version if useful. Owner must never be a purchaser; optionally restrict active/deactivated buyer behavior explicitly and test it.
- `GiftCoBuyer` or `ReservationParticipant`: join between reservation and additional purchasers, unique on `(reservation_id, user_id)`. The lead purchaser cannot also appear as a co-buyer. Joining/leaving must not create another reservation for the same wish.
- `ImageAsset`: optional minimal entity for metadata; store physical image files in a configurable **persistent external storage directory**, not inside the executable JAR or publicly listable web root. Store opaque generated names and metadata; avoid file-path traversal.
- `SinterklaasSettings`: shared target date/year and household budget agreement. Keep the countdown configurable without building full event planning.

**Rules:** IDs generated server-side; referential integrity and uniqueness enforced in PostgreSQL/Flyway. Prefer `Instant` timestamps (UTC storage) and proper timezone conversion in the UI (`Europe/Amsterdam`). Money uses `BigDecimal`, never `double`. Enum values require deliberate schema mapping and stable persistence strategy. Keep migrations idempotent only where appropriate; immutable versioned Flyway migrations should not be edited after application. Bootstrap the first admin via a secure documented bootstrap mechanism, never a default committed username/password.

## Critical authorization and surprise protection

- Every server-side query/action must enforce ownership and role rules. Hidden buttons are **not** access control.
- An ordinary user may edit only their own profile and wishes, and only their own purchaser-side reservations or purchases where explicitly permitted.
- A user may view another user's wishes; for other users' wishes show `available` or `reserved` and reservation actions, but do **not** reveal the purchasing users or order/status details unless they are a purchaser/co-buyer or admin.
- On **their own** wishes, show wish content but **never** reservation existence/status, buyers, gifts count, purchasing notes, participants or derived spoilers. No leaks via network response, HTML data attributes, error messages, print views, sorting or `HX-Trigger` payloads.
- Co-buyers can see the shared reservation and its participants. Only the lead purchaser (or admin) can modify workflow status / invite or remove co-buyers, unless an explicit test-backed policy says otherwise.
- Users cannot reserve their own wishes or become a co-buyer for their own wish. Admin can see/manage everything but should have a conspicuous reveal-warning on spoiler-prone screens.
- Any unauthorized direct URL access must return a suitable 403 or conceal resource existence with 404 where appropriate. Test horizontal privilege escalation, disabled users, and spoiler leakage.
- Treat authentication separately from UI rendering. Keep security checks in services and/or method authorization as well as web entry points.

## Concurrency and reservation invariants

The database is the final authority, not button state. Protect `GiftReservation.wish_id` with a unique database constraint. Reserve in a transaction; ensure only one request can create the reservation and handle a uniqueness violation as an ordinary business conflict (return an understandable message and refreshed fragment, not HTTP 500). An unreserve operation must not remove another purchaser's reservation. Choose and document the approach to unreserving co-purchased gifts (e.g., only lead buyer may cancel; cancel removes co-buyer associations atomically). Status transitions must be valid and cannot silently regress. Test competing reservations with two genuinely simultaneous transactions against PostgreSQL, not just unit-level mocks.

## Product-link fetching and image security

- Metadata scraping is a **best-effort** convenience, not a required success path. Preserve manually edited fields; a refresh must not silently overwrite manual input. Show source URL, retrieval time if useful, and clear error feedback.
- Validate `https` URLs (and explicitly decide whether `http` is allowed), reject credentials and dangerous schemes, block loopback/private/link-local/metadata-server IP ranges, validate every DNS resolution and redirect hop, enforce timeouts, response-size limits, redirect limits and allowed HTML content types. Defend against SSRF and DNS rebinding; if robust network safeguards cannot be implemented, disable arbitrary server-side fetching and document why.
- Sanitize or safely render all scraped strings as plain text; do not output scraped HTML. Avoid automatic remote image hotlinks if possible; preview only controlled validated URLs or download with the same network security restrictions.
- Limit upload size and count; only accept verified image formats (e.g. JPEG, PNG, WebP), verify content signature, prevent SVG and executable content, generate safe random filenames and constrain dimensions. Serve via authorized application endpoints, with safe content headers. Clean up replaced files safely.

## UI and frontend design

- Site navigation: sign-in, dashboard, participants, my wishes, selected gifts, profile/theme preferences, admin section (role-gated).
- Consistent mobile-first Bootstrap layout, proper labels, validation messages in Dutch, empty states, accessibility and keyboard usability. Distinguish gift status using both text and color.
- Thymeleaf provides full pages and reusable fragments; HTMX requests return only the appropriate fragments, including refreshed CSRF-safe forms and messages. Keep ordinary form POST/redirect/GET fallback when HTMX is disabled.
- Keep CSRF enabled for all state changes, including `hx-post`, `hx-put` and `hx-delete`; ensure an authenticated session cannot leak through caches or history. Do not place secrets in JavaScript-readable data or URLs.
- Theme tokens should cover backgrounds, surfaces, text, links, accents, statuses, cards and button states; use Bootstrap's `data-bs-theme` and CSS variables. Provide Sinterklaas-themed and neutral palettes with light/dark variants and a system-follow option; persist preferences server-side for authenticated users. Verify contrast in every mode. Avoid hardcoding palette colors in templates.
- Print styles hide navigation and action controls; ensure print routes, generated pages and browser print content obey identical spoiler authorization.

## Suggested package organization

Use **package-by-feature** rather than large global controller/service/repository directories. Suggested root: `nl.xanvier.sinterklaas` (check actual package first).

```text
nl.xanvier.sinterklaas/
  config/            # security, application settings, shared web concerns
  user/              # users, profile, password, avatars
  invitation/        # admin-created invitation tokens and redemption
  wish/              # wishes, images, product metadata enrichment
  gift/              # reservations, co-buyers, workflow statuses
  household/         # default group and informational budget agreement
  dashboard/         # purchaser-only widgets and counts
  theme/             # theme settings and selection
  shared/            # narrow shared utilities and exception handling
```

Spring MVC controllers deal with request/response DTOs, not mutable entities directly. Business rules belong in services. Repositories contain persistence access. Use Bean Validation on inputs, DTOs/forms for binding, and mapper methods where useful. Avoid `@Data` on JPA entities, recursive `toString()`, careless Lombok equality/hashcode on mutable entities, eager association loading by default, or leaking entities into Thymeleaf views. Favor small focused classes over abstraction for its own sake.

## Required screens and user journeys

1. Sign-in / sign-out, account disabled feedback, initial-admin bootstrap guidance.
2. Admin: list/create/edit/disable/reactivate users; issue/cancel invitations and copy redemption links; edit household budget and target date; access admin-only reservation audit with spoiler warning.
3. Invitation acceptance: validate token, create username/password/profile, consume invitation, login or redirect to login.
4. Own profile: view/edit personal data, upload avatar, change password, select theme/color mode.
5. Own wishes: list, create, edit, remove, upload photo, submit product URL and request metadata lookup, set priority/price/variants/notes. Show no incoming reservation signals.
6. Other participants: browse their wishes and availability, reserve/unreserve, start joint buying or join with lead buyer's permission, view/update eligible gift statuses.
7. My gifts: private reserved purchases grouped by recipient with progress/status and co-buyers; permit print view.
8. Dashboard: purchase summary, next actions, countdown, safe gift counts (never expose counts for the logged-in recipient's gifts).
9. Print-friendly own purchase list and authorized wish list, no hidden details accidentally embedded.

## Quality bar and automated tests

Use JUnit 5, Spring Security test support, MVC slice/integration tests and PostgreSQL-backed integration tests. Add validation dependency and Testcontainers if appropriate. Every selected feature must have an acceptance test at the relevant level.

**Highest-priority test cases:**

- Login works; invalid credentials fail; disabled user is blocked; role-limited endpoints reject unauthorized users; no public registration.
- Admin invitations are single-use, time-limited and not replayable; bootstrap does not ship with a default password.
- Change-password verifies existing password and hashes new value; stored password is never readable.
- A user's own wish view contains no reservation status or purchasing data, even after purchase/status changes, including HTMX and print responses.
- User A cannot reserve own wish, edit user B's wish, access user B's purchase list, or bypass constraints with forged requests.
- Two concurrent buyers try to reserve the same wish: **exactly one succeeds**; buyer-facing conflict handled properly.
- Multiple purchasers can join a reservation without causing duplicate gifts; only authorized parties see participants or change state.
- Reservation workflow transitions and cancellation produce valid, transactionally consistent states.
- Gift counts count a shared gift once and never spoil the recipient's surprises.
- Product metadata fails gracefully when scraping is blocked/malformed; prevent SSRF and unsafe redirects.
- Upload validation rejects unsafe types, excessive sizes, and path traversal; only authorized file access works.
- Responsive pages have consistent layouts and accessibility-friendly controls; color mode/palette preferences persist and do not leak across users.
- Flyway migrates a clean PostgreSQL database; repeated startup preserves integrity. Run `./mvnw test` if Maven wrapper exists, otherwise `mvn test`; run `./mvnw verify` / `mvn verify` where integration tests are bound.

## Implementation order / definition of done

Work in phases, keeping each stage runnable:

1. **Project foundation:** inspect/validate Maven POM, add minimal missing dependencies, configure PostgreSQL/Flyway and environment-driven secrets, base layout and security setup; document local startup.
2. **Accounts:** schema, initial admin, role gates, login, admin CRUD, deactivate, invitations, profile and password change, avatar uploads.
3. **Wishes:** schema, owner CRUD, browse others, product URLs/metadata, wish images, priority, price, variants and descriptions.
4. **Purchases:** transactionally correct reserve/unreserve, privacy rules, buyer dashboard, gift statuses, co-buying, gift counts and print views.
5. **Household and experience:** informational budget, countdown, mobile polish, HTMX enhancement, theming and accessible styling.
6. **Hardening:** PostgreSQL integration/security/concurrency tests; upload/SSRF protection; error handling; docs; final regression check.

At the end of every phase: run build/tests, fix failures, check all previously implemented workflows, summarize what changed and what remains. Do not mark a feature complete if the backend works but the user-facing screen is unusable, or vice versa. A feature is complete only when its persistence, authorization, web flow, errors, and tests work.

## Operational configuration and documentation

- Provide `application.yml` with placeholders or environment bindings and a `.env.example` **without real secrets**. Do not commit credentials. Local PostgreSQL via optional Docker Compose is fine; do not require cloud services.
- Configure upload storage, maximum upload size, JDBC URL/credentials, invite lifetime, session/cookie security and application base URL. Explain reverse-proxy HTTPS setup; use secure cookies in production.
- README must explain starting the app, provisioning the initial admin, inviting the first user, running Flyway migrations, running tests and viewing the app.
- Do not store product credentials or third-party scraping tokens; do not log passwords, invite tokens, session IDs or surprise details.
- Preserve existing user data across releases. Add indexes and pagination if ordinary lists become large, not speculative scaling machinery.

## Final acceptance checklist

- [ ] All nine baseline requirements implemented.
- [ ] All 21 owner-selected numbered features implemented: 1, 4, 5, 7, 8, 9, 10, 11, 12, 13, 16, 21, 28, 34, 35, 36, 39, 41, 42, 43, 44.
- [ ] Every sensitive view and mutation is authorization-tested; no gift spoilers for recipients.
- [ ] No duplicate reservations under PostgreSQL concurrency.
- [ ] No public registration, unauthorized new product features, or new frontend/backend framework without a compelling reason.
- [ ] Local app startup, Flyway, Maven tests, theme switching and printing function correctly.
- [ ] User-facing strings are Dutch and README explains local operation.

## Startup verification after changes

After every implementation change that affects source code, configuration, migrations, or templates, start the application using the normal local startup command (`./mvnw spring-boot:run`) and confirm that Flyway, Hibernate, and the web server all initialize successfully. Do not stop at compilation. If startup fails, read the first underlying exception, fix it, and repeat the startup check. Stop the running application after confirming startup. A unit-test run is optional unless specifically requested or needed to diagnose a failure.
