package nl.xanvier.sinterklaas.invitation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitation> findByTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitation> findLockedById(UUID id);

    List<Invitation> findAllByOrderByCreatedAtDesc();
}
