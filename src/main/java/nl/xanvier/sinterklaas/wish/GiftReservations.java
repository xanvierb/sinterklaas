package nl.xanvier.sinterklaas.wish;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import nl.xanvier.sinterklaas.user.CurrentUser;
import nl.xanvier.sinterklaas.user.FamilyAccess;
import nl.xanvier.sinterklaas.user.LoginUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class GiftReservations {
    enum Availability { RESERVED, MINE }

    record GiftStep(GiftStatus status, String label, boolean reached, boolean current, String time, String timeLabel, String pickerTime) {}
    record TrackedGift(UUID wishId, String title, String productUrl, boolean hasImage, GiftStatus status, GiftStatus nextStatus,
                       GiftStatus previousStatus, String nextLabel, String previousLabel, boolean inStore, String shopName, List<GiftStep> timeline) {
        public String statusLabel() { return status.label(inStore); }
    }
    record Recipient(UUID id, String name) {}

    record GiftTasks(long toOrder, long toBuy, long awaitingDelivery, long toWrap, long toGive) {
        public long remaining() { return toOrder + toBuy + awaitingDelivery + toWrap + toGive; }
    }

    GiftTasks tasks() {
        UUID buyerId = buyer().id();
        Map<GiftStatus, Long> counts = new java.util.EnumMap<>(GiftStatus.class);
        long toBuy = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                WHERE r.buyer_id = ? AND r.status = 'RESERVED' AND w.in_store = TRUE
                """, Long.class, buyerId);
        jdbc.query("""
                SELECT status, COUNT(*) AS total
                FROM gift_reservation WHERE buyer_id = ? GROUP BY status
                """, row -> {
            counts.put(GiftStatus.valueOf(row.getString("status")), row.getLong("total"));
        }, buyerId);
        return new GiftTasks(Math.max(0, counts.getOrDefault(GiftStatus.RESERVED, 0L) - toBuy), toBuy,
                counts.getOrDefault(GiftStatus.ORDERED, 0L),
                counts.getOrDefault(GiftStatus.RECEIVED, 0L),
                counts.getOrDefault(GiftStatus.WRAPPED, 0L));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<Recipient, List<TrackedGift>> trackedGifts(GiftStatus filter, Boolean inStoreFilter) {
        UUID buyerId = buyer().id();
        record Reached(UUID wishId, GiftStatus status, OffsetDateTime time) {}
        var dates = jdbc.query("""
                SELECT wish_id, status, reached_at FROM (
                    SELECT h.wish_id, h.status, COALESCE(h.display_at, h.changed_at) AS reached_at,
                           ROW_NUMBER() OVER (PARTITION BY h.wish_id, h.status ORDER BY h.changed_at DESC, h.id DESC) AS position
                    FROM gift_status_change h JOIN gift_reservation r ON r.wish_id = h.wish_id
                    WHERE r.buyer_id = ?
                ) history WHERE position = 1
                """, (row, number) -> new Reached(row.getObject("wish_id", UUID.class),
                GiftStatus.valueOf(row.getString("status")), row.getObject("reached_at", OffsetDateTime.class)), buyerId)
                .stream().collect(Collectors.groupingBy(Reached::wishId,
                        Collectors.toMap(Reached::status, Reached::time)));
        record Row(Recipient recipient, TrackedGift gift) {}
        List<Row> rows = jdbc.query("""
                SELECT w.id, w.title, w.product_url, w.image_id, u.id AS recipient_id, u.display_name, r.status, r.created_at, w.in_store, w.shop_name
                FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                JOIN app_user u ON u.id = w.owner_id
                WHERE r.buyer_id = ?
                ORDER BY u.display_name, u.id, r.created_at, w.id
                """, (row, number) -> {
            GiftStatus status = GiftStatus.valueOf(row.getString("status"));
            UUID wishId = row.getObject("id", UUID.class);
            OffsetDateTime reservedAt = row.getObject("created_at", OffsetDateTime.class);
            var reached = dates.getOrDefault(wishId, Map.of());
            boolean inStore = row.getBoolean("in_store");
            GiftStatus next = status.adjacent(inStore, false);
            GiftStatus previous = status.adjacent(inStore, true);
            var timeline = GiftStatus.stages(inStore).stream().map(step -> {
                boolean pastOrCurrent = step.ordinal() <= status.ordinal();
                OffsetDateTime time = pastOrCurrent
                        ? reached.getOrDefault(step, step == GiftStatus.RESERVED ? reservedAt : null) : null;
                return new GiftStep(step, step.label(inStore), pastOrCurrent, step == status,
                        time == null ? null : time.toInstant().toString(),
                        time == null ? null : GIFT_TIME.format(time),
                        time == null ? null : GIFT_PICKER_TIME.format(time));
            }).toList();
            return new Row(new Recipient(row.getObject("recipient_id", UUID.class), row.getString("display_name")),
                    new TrackedGift(wishId, row.getString("title"), row.getString("product_url"),
                            row.getObject("image_id") != null, status, next, previous, next == null ? null : next.label(inStore),
                            previous == null ? null : previous.label(inStore), inStore, row.getString("shop_name"), timeline));
        }, buyerId);
        return rows.stream().filter(row -> filter == null || row.gift().status() == filter)
                .filter(row -> inStoreFilter == null || row.gift().inStore() == inStoreFilter)
                .collect(Collectors.groupingBy(Row::recipient, LinkedHashMap::new,
                Collectors.mapping(Row::gift, Collectors.toList())));
    }

    @Transactional
    public boolean moveStatus(UUID wishId, GiftStatus expected, boolean back) {
        UUID buyerId = buyer().id();
        var modes = jdbc.queryForList("""
                SELECT w.in_store FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                WHERE r.wish_id = ? AND r.buyer_id = ? AND r.status = ?
                """, Boolean.class, wishId, buyerId, expected.name());
        if (modes.isEmpty()) return false;
        GiftStatus next = expected.adjacent(modes.getFirst(), back);
        boolean updated = next != null && jdbc.update("""
                UPDATE gift_reservation SET status = ?
                WHERE wish_id = ? AND buyer_id = ? AND status = ?
                """, next.name(), wishId, buyerId, expected.name()) == 1;
        if (updated) {
            jdbc.update("INSERT INTO gift_status_change (id, wish_id, status) VALUES (?, ?, ?)",
                    UUID.randomUUID(), wishId, next.name());
        }
        return updated;
    }

    @Transactional
    public boolean editGiftTime(UUID wishId, GiftStatus step, LocalDateTime time) {
        UUID buyerId = buyer().id();
        var statuses = jdbc.queryForList("""
                SELECT status FROM gift_reservation WHERE wish_id = ? AND buyer_id = ? FOR UPDATE
                """, String.class, wishId, buyerId);
        if (statuses.isEmpty() || step.ordinal() > GiftStatus.valueOf(statuses.getFirst()).ordinal()) return false;
        if (step == GiftStatus.ORDERED && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT in_store FROM wish WHERE id = ?", Boolean.class, wishId))) return false;
        var offsets = AMSTERDAM.getRules().getValidOffsets(time);
        if (offsets.isEmpty()) return false;
        OffsetDateTime displayAt = time.atOffset(offsets.getFirst());
        var events = jdbc.queryForList("""
                SELECT id FROM gift_status_change WHERE wish_id = ? AND status = ?
                ORDER BY changed_at DESC, id DESC LIMIT 1
                """, UUID.class, wishId, step.name());
        if (!events.isEmpty()) {
            return jdbc.update("UPDATE gift_status_change SET display_at = ? WHERE id = ?",
                    displayAt, events.getFirst()) == 1;
        }
        if (step != GiftStatus.RESERVED) return false;
        return jdbc.update("""
                INSERT INTO gift_status_change (id, wish_id, status, changed_at, display_at)
                SELECT ?, wish_id, 'RESERVED', created_at, ? FROM gift_reservation WHERE wish_id = ?
                """, UUID.randomUUID(), displayAt, wishId) == 1;
    }

    private record Claim(UUID wishId, Availability availability) {}

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final FamilyAccess family;

    record ChatSummary(UUID wishId, UUID imageId, String title, String otherName, long unread, boolean buyer, UUID avatarOwnerId) {}
    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final DateTimeFormatter GIFT_PICKER_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss").withZone(AMSTERDAM);
    private static final DateTimeFormatter GIFT_TIME = DateTimeFormatter
            .ofPattern("d MMM yyyy, HH:mm:ss", Locale.forLanguageTag("nl-NL"))
            .withZone(ZoneId.of("Europe/Amsterdam"));
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter
            .ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("nl-NL"))
            .withZone(ZoneId.of("Europe/Amsterdam"));

    record ChatMessage(boolean mine, boolean sameSide, String body, String sentAt, String sentAtLabel) {}
    record ChatThread(UUID wishId, String title, String otherName, boolean buyer, UUID avatarOwnerId, boolean childRecipient, List<ChatMessage> messages) {}

    GiftReservations(JdbcTemplate jdbc, CurrentUser current, FamilyAccess family) {
        this.jdbc = jdbc;
        this.current = current;
        this.family = family;
    }

    Map<UUID, Availability> availability(UUID ownerId) {
        LoginUser viewer = current.require();
        if (viewer.id().equals(ownerId)) return Map.of();
        return jdbc.query("""
                SELECT r.wish_id, r.buyer_id
                FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                WHERE w.owner_id = ?
                """, (row, number) -> new Claim(row.getObject("wish_id", UUID.class),
                viewer.id().equals(row.getObject("buyer_id", UUID.class))
                        ? Availability.MINE : Availability.RESERVED), ownerId)
                .stream().collect(Collectors.toMap(Claim::wishId, Claim::availability));
    }

    List<UUID> lockedWishes(UUID owner) {
        if (!family.manages(current.require(), owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return jdbc.queryForList("""
                SELECT r.wish_id FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                WHERE w.owner_id = ?
                """, UUID.class, owner);
    }

    boolean isReserved(UUID wishId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM gift_reservation WHERE wish_id = ?)", Boolean.class, wishId));
    }

    @Transactional
    public boolean reserve(UUID ownerId, UUID wishId) {
        LoginUser participant = buyer();
        UUID buyerId = participant.id();
        jdbc.queryForList("SELECT id FROM app_user WHERE id = ? FOR UPDATE", UUID.class, ownerId);
        if (family.manages(participant, ownerId)) throw new AccessDeniedException("Dit is je eigen wens.");
        if (jdbc.queryForList("SELECT id FROM wish WHERE id = ? AND owner_id = ? FOR UPDATE",
                UUID.class, wishId, ownerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (isReserved(wishId)) return false;
        int inserted = jdbc.update("""
                INSERT INTO gift_reservation (wish_id, buyer_id)
                SELECT w.id, ? FROM wish w JOIN app_user u ON u.id = w.owner_id
                WHERE w.id = ? AND w.owner_id = ? AND u.enabled = TRUE AND u.role = 'USER'
                """, buyerId, wishId, ownerId);
        if (inserted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return true;
    }

    @Transactional
    public void cancel(UUID wishId) {
        UUID buyerId = buyer().id();
        jdbc.queryForList("SELECT id FROM wish WHERE id = ? FOR UPDATE", UUID.class, wishId);
        int deleted = jdbc.update("DELETE FROM gift_reservation WHERE wish_id = ? AND buyer_id = ?",
                wishId, buyerId);
        if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    record AdminReservation(UUID wishId, String title, String recipient, String buyer, String status) {}

    List<AdminReservation> adminReservations() {
        current.requireAdmin();
        return jdbc.query("""
                SELECT w.id, w.title, owner.display_name AS recipient, buyer.display_name AS purchaser,
                       w.in_store, r.status
                FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                JOIN app_user owner ON owner.id = w.owner_id
                JOIN app_user buyer ON buyer.id = r.buyer_id
                ORDER BY owner.display_name, w.title, w.id
                """, (row, number) -> new AdminReservation(row.getObject("id", UUID.class),
                row.getString("title"), row.getString("recipient"), row.getString("purchaser"),
                GiftStatus.valueOf(row.getString("status")).label(row.getBoolean("in_store"))));
    }

    @Transactional
    public boolean adminCancel(UUID wishId) {
        current.requireAdmin();
        jdbc.queryForList("SELECT id FROM wish WHERE id = ? FOR UPDATE", UUID.class, wishId);
        return jdbc.update("DELETE FROM gift_reservation WHERE wish_id = ?", wishId) == 1;
    }

    String productUrl(UUID wishId) {
        return jdbc.query("""
                SELECT w.product_url FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                WHERE r.wish_id = ? AND r.buyer_id = ? AND w.product_url IS NOT NULL
                """, (row, number) -> row.getString(1), wishId, buyer().id())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    long unreadChats() {
        UUID viewer = buyer().id();
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                JOIN app_user owner ON owner.id = w.owner_id
                JOIN app_user buyer ON buyer.id = r.buyer_id
                WHERE (%s OR r.buyer_id = ?) AND owner.enabled = TRUE AND buyer.enabled = TRUE
                  AND EXISTS (SELECT 1 FROM reservation_message m
                              WHERE m.wish_id = r.wish_id AND ((m.sender_id = r.buyer_id) <> (r.buyer_id = ?))
                          AND NOT EXISTS (SELECT 1 FROM reservation_message_read mr
                                          WHERE mr.message_id = m.id AND mr.reader_id = ?))
                """.formatted(FamilyAccess.RECIPIENT), Long.class, viewer, viewer, viewer, viewer, viewer);
        return count == null ? 0 : count;
    }

    List<ChatSummary> chats() {
        UUID viewer = buyer().id();
        return jdbc.query("""
                SELECT r.wish_id, w.image_id, w.title, r.buyer_id, owner.id AS owner_id, owner.avatar,
                       CASE WHEN r.buyer_id = ? THEN owner.display_name ELSE 'Sint en Piet' END AS other_name,
                       (SELECT COUNT(*) FROM reservation_message m
                        WHERE m.wish_id = r.wish_id AND ((m.sender_id = r.buyer_id) <> (r.buyer_id = ?))
                          AND NOT EXISTS (SELECT 1 FROM reservation_message_read mr
                                          WHERE mr.message_id = m.id AND mr.reader_id = ?)) AS unread
                FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                JOIN app_user owner ON owner.id = w.owner_id
                JOIN app_user buyer ON buyer.id = r.buyer_id
                WHERE (%s OR r.buyer_id = ?) AND owner.enabled = TRUE AND buyer.enabled = TRUE
                AND EXISTS (SELECT 1 FROM reservation_message m WHERE m.wish_id = r.wish_id)
                ORDER BY unread DESC, r.created_at DESC, r.wish_id
                """.formatted(FamilyAccess.RECIPIENT), (row, number) -> {
            boolean isBuyer = viewer.equals(row.getObject("buyer_id", UUID.class));
            UUID avatarOwnerId = isBuyer && row.getObject("avatar") != null
                    ? row.getObject("owner_id", UUID.class) : null;
            return new ChatSummary(row.getObject("wish_id", UUID.class), row.getObject("image_id", UUID.class),
                    row.getString("title"), row.getString("other_name"), row.getLong("unread"), isBuyer, avatarOwnerId);
        },
                viewer, viewer, viewer, viewer, viewer, viewer);
    }

    @Transactional
    public ChatThread thread(UUID wishId) {
        LoginUser viewer = current.require();
        if (viewer.admin()) throw new AccessDeniedException("Beheerders hebben geen toegang tot privéchats.");
        jdbc.queryForList("SELECT id FROM wish WHERE id = ? FOR UPDATE", UUID.class, wishId);
        record Details(String title, String name, boolean buyer, UUID avatarOwnerId, boolean childRecipient) {}
        List<Details> details = jdbc.query("""
                SELECT w.title, r.buyer_id, owner.display_name AS owner_name, owner.id AS owner_id, owner.avatar, owner.child_profile
                FROM gift_reservation r JOIN wish w ON w.id=r.wish_id
                JOIN app_user owner ON owner.id=w.owner_id
                JOIN app_user buyer ON buyer.id=r.buyer_id
                WHERE r.wish_id=? AND (%s OR r.buyer_id=?) AND owner.enabled=TRUE AND buyer.enabled=TRUE
                """.formatted(FamilyAccess.RECIPIENT), (row, number) -> {
            boolean isBuyer = viewer.id().equals(row.getObject("buyer_id", UUID.class));
            return new Details(row.getString("title"),
                    isBuyer ? row.getString("owner_name") : "Sint en Piet", isBuyer,
                    isBuyer && row.getObject("avatar") != null ? row.getObject("owner_id", UUID.class) : null,
                    row.getBoolean("child_profile"));
        }, wishId, viewer.id(), viewer.id(), viewer.id());
        if (details.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Details detail = details.getFirst();
        List<ChatMessage> messages = jdbc.query("""
                SELECT m.sender_id, m.body, m.created_at, r.buyer_id
                FROM reservation_message m
                JOIN gift_reservation r ON r.wish_id=m.wish_id JOIN wish w ON w.id=r.wish_id
                WHERE m.wish_id=? AND (%s OR r.buyer_id=?)
                ORDER BY m.created_at, m.id
                """.formatted(FamilyAccess.RECIPIENT), (row, number) -> {
            var sentAt = row.getObject("created_at", OffsetDateTime.class).toInstant();
            UUID sender = row.getObject("sender_id", UUID.class);
            boolean sameSide = detail.buyer() == sender.equals(row.getObject("buyer_id", UUID.class));
            return new ChatMessage(viewer.id().equals(sender), sameSide,
                    row.getString("body"), sentAt.toString(), LOCAL_TIME.format(sentAt));
        }, wishId, viewer.id(), viewer.id(), viewer.id());
        jdbc.update("""
                INSERT INTO reservation_message_read (message_id, reader_id)
                SELECT m.id, ? FROM reservation_message m JOIN gift_reservation r ON r.wish_id = m.wish_id
                WHERE m.wish_id = ? AND ((m.sender_id = r.buyer_id) <> (r.buyer_id = ?))
                  AND NOT EXISTS (SELECT 1 FROM reservation_message_read mr
                                  WHERE mr.message_id = m.id AND mr.reader_id = ?)
                """, viewer.id(), wishId, viewer.id(), viewer.id());
        return new ChatThread(wishId, detail.title(), detail.name(), detail.buyer(), detail.avatarOwnerId(), detail.childRecipient(), messages);
    }

    @Transactional
    public void send(UUID wishId, String body) {
        LoginUser user = current.require();
        if (user.admin()) throw new AccessDeniedException("Privégesprek.");
        String clean = body.strip();
        if (clean.isEmpty() || clean.length() > 2000) {
            throw new nl.xanvier.sinterklaas.shared.Problem("Gebruik 1 tot 2000 tekens.");
        }
        if (jdbc.queryForList("SELECT id FROM wish WHERE id = ? FOR UPDATE", UUID.class, wishId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        int inserted = jdbc.update("""
                INSERT INTO reservation_message (id, wish_id, sender_id, body)
                SELECT ?, r.wish_id, ?, ?
                FROM gift_reservation r JOIN wish w ON w.id = r.wish_id
                JOIN app_user owner ON owner.id = w.owner_id
                JOIN app_user buyer ON buyer.id = r.buyer_id
                WHERE r.wish_id = ? AND (%s OR r.buyer_id = ?)
                  AND owner.enabled = TRUE AND buyer.enabled = TRUE
                """.formatted(FamilyAccess.RECIPIENT), UUID.randomUUID(), user.id(), clean, wishId, user.id(), user.id(), user.id());
        if (inserted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private LoginUser buyer() {
        LoginUser user = current.require();
        if (user.admin()) throw new AccessDeniedException("Beheerders kunnen geen cadeaus reserveren.");
        return user;
    }
}
