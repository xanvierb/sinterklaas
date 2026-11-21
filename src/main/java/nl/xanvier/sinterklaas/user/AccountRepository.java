package nl.xanvier.sinterklaas.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUsername(String username);

    List<Account> findAllByOrderByDisplayNameAscIdAsc();

    List<Account> findAllByEnabledTrueAndRoleOrderByDisplayNameAscIdAsc(Role role);

    long countByEnabledTrueAndRole(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findLockedById(UUID id);
}
