# ADR-038: Polling for Notification Delivery

**Status**: Accepted

**Context**: Phase 6.5 adds an in-app notification system with a bell icon in the application header showing an unread count badge. When a notification is created (e.g., task assigned, comment added), the user needs to see the updated count in their browser. The question is how the frontend learns about new notifications.

The platform is a B2B project management tool, not a real-time chat application. Users check tasks, upload documents, and review timelines -- activities where a 30-60 second notification delay is acceptable. The existing frontend (Next.js 16 with App Router) makes REST API calls to the Spring Boot 4 backend. There is no WebSocket or Server-Sent Events infrastructure.

**Options Considered**:

1. **Short polling (30-60 second interval)**
   - Pros:
     - Simplest implementation -- `setInterval` + `fetch` in a client component
     - No infrastructure changes -- uses existing REST API and HTTP/1.1
     - Lightweight query: `SELECT count(*) FROM notifications WHERE recipient_member_id = ? AND is_read = false` hits a composite index
     - Predictable server load -- each active user generates 1-2 requests per minute
     - Easy to implement, test, and debug
     - Works behind any proxy, CDN, or load balancer without configuration
   - Cons:
     - 30-60 second delay between notification creation and badge update
     - Wasted requests when there are no new notifications (most polls return the same count)
     - Server load scales linearly with active users (mitigated by the lightweight query)

2. **Long polling (hold connection until new notification or timeout)**
   - Pros:
     - Near-instant notification delivery
     - Fewer wasted requests than short polling
   - Cons:
     - Holds server threads/connections open for extended periods
     - Complex timeout and reconnection logic
     - Load balancers and proxies may terminate long-held connections
     - Spring Boot's thread model (even with virtual threads) is not optimized for long-held connections
     - More complex error handling (connection drops, timeouts, retries)

3. **Server-Sent Events (SSE)**
   - Pros:
     - Push-based -- server sends notifications as they happen
     - Native browser support via `EventSource` API
     - Unidirectional -- simpler than WebSocket for notification-only use cases
     - HTTP/2 multiplexing reduces connection overhead
   - Cons:
     - Requires persistent HTTP connection per active user
     - Spring Boot 4 SSE support requires `SseEmitter` or WebFlux -- neither is configured in the codebase
     - Connection management (reconnection on drop, session affinity with load balancers)
     - ECS/Fargate load balancer must support persistent connections (ALB does, but requires configuration)
     - Not supported by all corporate proxies and firewalls

4. **WebSocket**
   - Pros:
     - Full-duplex -- server can push notifications instantly
     - Efficient for high-frequency updates
     - Well-supported in modern browsers
   - Cons:
     - Heaviest infrastructure requirement -- WebSocket upgrade, session management, heartbeat, reconnection
     - Spring Boot WebSocket requires `spring-boot-starter-websocket` and significant configuration
     - Load balancer must support WebSocket upgrade (ALB sticky sessions needed)
     - Overkill for a notification count -- full-duplex is not needed for server-to-client-only updates
     - Significantly increases the operational surface (monitoring, debugging, scaling)

**Decision**: Short polling with a 30-second interval (Option 1).

**Rationale**: Short polling is the right choice for Phase 6.5 given the platform's nature and current infrastructure:

1. **Acceptable latency**: A 30-second delay for notification delivery is imperceptible in a project management workflow. Users are not waiting for instant chat messages -- they are being notified about task assignments, comments, and document uploads that happened minutes or hours ago. The notification is still timely; it just does not need to be instant.

2. **Zero infrastructure changes**: Polling uses the existing REST API, HTTP/1.1, and ALB configuration. No WebSocket upgrade, no persistent connections, no new Spring dependencies. The backend serves a lightweight `GET /api/notifications/unread-count` endpoint that hits an indexed query returning a single integer.

3. **Predictable load**: With 30-second polling, 100 concurrent users generate ~200 requests per minute (~3.3 req/s). The unread count query is an index-only scan (`idx_notifications_unread` on `recipient_member_id, is_read, created_at DESC`) that completes in microseconds. This is negligible compared to the platform's regular API traffic.

4. **Testability**: Polling is trivially testable -- the frontend component calls `fetch` on an interval, and the backend returns a JSON count. No WebSocket handshake, no SSE stream, no connection lifecycle to test.

The migration path to push-based delivery (when/if needed) is straightforward:
1. Add `spring-boot-starter-websocket` or implement SSE via `SseEmitter`
2. Replace the polling `setInterval` in the frontend with an `EventSource` (SSE) or `WebSocket` connection
3. The `NotificationEventHandler` gains an additional step: after creating notification rows, it pushes to the connected recipient's SSE/WebSocket channel
4. The REST API endpoints remain unchanged -- they serve as fallback and for the full notifications page

**Consequences**:
- Frontend `NotificationBell` component uses `useEffect` + `setInterval(30_000)` to poll `GET /api/notifications/unread-count`
- Polling starts when the app shell mounts, stops when it unmounts (cleanup in `useEffect` return)
- The polling interval is a constant (not configurable per user) -- simplifies implementation
- `GET /api/notifications/unread-count` returns `{ "count": N }` -- a single integer from an indexed query
- No WebSocket, SSE, or long-polling infrastructure in Phase 6.5
- Users experience up to 30 seconds of delay between notification creation and badge update
- The full notifications page and dropdown list are fetched on-demand (click to open), not polled
- Future push-based delivery requires infrastructure changes but no API changes
