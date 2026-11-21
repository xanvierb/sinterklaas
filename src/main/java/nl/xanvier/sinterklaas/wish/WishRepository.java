package nl.xanvier.sinterklaas.wish;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface WishRepository extends JpaRepository<Wish, UUID> {
    List<Wish> findByOwnerIdOrderByPriorityDescUpdatedAtDesc(UUID ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Wish> findLockedById(UUID id);

    Optional<Wish> findByIdAndOwnerId(UUID id, UUID ownerId);
}
