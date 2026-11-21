package nl.xanvier.sinterklaas.user;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import nl.xanvier.sinterklaas.shared.Images;
import nl.xanvier.sinterklaas.shared.Problem;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService implements UserDetailsService {

    public record Person(UUID id, String displayName, boolean hasAvatar, boolean enabled, ChocolateFlavor chocolateFlavor, boolean childProfile) {}

    public record AccountView(Person person, String username, Role role, ColorMode colorMode, Palette palette) {}

    private final AccountRepository accounts;
    private final CurrentUser current;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Images images;

    AccountService(AccountRepository accounts, CurrentUser current, PasswordEncoder encoder, Clock clock, Images images) {
        this.accounts = accounts;
        this.current = current;
        this.encoder = encoder;
        this.clock = clock;
        this.images = images;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return accounts.findByUsername(normalize(username)).filter(account -> !account.isChildProfile()).map(LoginUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Onbekend account"));
    }

    @Transactional(readOnly = true)
    public boolean sessionValid(LoginUser user) {
        return accounts.findById(user.id())
                .filter(a -> a.isEnabled() && a.getAuthRevision() == user.revision()).isPresent();
    }

    @Transactional(readOnly = true)
    public AccountView own() {
        return view(account(current.require().id()));
    }

    @Transactional(readOnly = true)
    public List<Person> participants() {
        current.require();
        return accounts.findAllByEnabledTrueAndRoleOrderByDisplayNameAscIdAsc(Role.USER).stream().map(AccountService::person).toList();
    }

    @Transactional(readOnly = true)
    public Person participant(UUID id) {
        LoginUser user = current.require();
        Account account = account(id);
        if (!account.isEnabled() && !user.admin() && !user.id().equals(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return person(account);
    }

    @Transactional(readOnly = true)
    public UUID avatar(UUID id) {
        participant(id);
        return account(id).getAvatar();
    }

    @Transactional(readOnly = true)
    public List<AccountView> all() {
        current.requireAdmin();
        return accounts.findAllByOrderByDisplayNameAscIdAsc().stream().map(AccountService::view).toList();
    }

    @Transactional(readOnly = true)
    public AccountView managed(UUID id) {
        current.requireAdmin();
        return view(account(id));
    }

    @Transactional
    public void create(AccountForms.NewAccount form, Role role) {
        adminWriteLock();
        createAccount(form, role);
    }

    @Transactional
    public void createFromInvitation(AccountForms.NewAccount form, UUID avatar, ChocolateFlavor chocolateFlavor) {
        Account account = newAccount(form, Role.USER);
        account.profile(account.getDisplayName(), account.getColorMode(), account.getPalette(),
                chocolateFlavor, avatar, clock.instant());
        accounts.save(account);
    }

    @Transactional
    public void edit(UUID id, AccountForms.EditAccount form, MultipartFile photo) {
        adminWriteLock();
        Account account = locked(id);
        if (account.isChildProfile() && form.role() != Role.USER) {
            throw new Problem("Een kinderprofiel kan geen beheerder worden.");
        }
        if (account.isEnabled() && account.getRole() == Role.ADMIN
                && (!form.enabled() || form.role() != Role.ADMIN) && accounts.countByEnabledTrueAndRole(Role.ADMIN) == 1) {
            throw new Problem("Er moet minstens één actieve beheerder blijven.");
        }
        String name = normalize(form.username());
        checkUsername(name, id);
        UUID avatar = images.replace(account.getAvatar(), photo);
        account.edit(name, form.displayName().strip(), form.role(), form.enabled(), avatar, clock.instant());
    }

    @Transactional
    public void editProfile(AccountForms.Profile form, MultipartFile photo) {
        Account user = locked(current.require().id());
        UUID avatar = images.replace(user.getAvatar(), photo);
        user.profile(form.displayName().strip(), form.colorMode(), Palette.SINTERKLAAS, form.chocolateFlavor(), avatar, clock.instant());
    }

    @Transactional
    public void changePassword(AccountForms.Password form) {
        Account user = locked(current.require().id());
        if (form.oldPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(form.oldPassword(), user.getPasswordHash())) {
            throw new Problem("Het huidige wachtwoord klopt niet.");
        }
        user.password(hash(form.password(), form.confirmation()), clock.instant());
    }

    @Transactional
    public void bootstrap(String username, String password) {
        if (username.isBlank() && password.isEmpty()) {
            return;
        }
        if (accounts.count() == 0) {
            createAccount(new AccountForms.NewAccount(username, "Beheerder", password, password), Role.ADMIN);
        }
    }

    private void adminWriteLock() {
        LoginUser user = current.requireAdmin();
        if (!sessionValid(user)) {
            throw new org.springframework.security.access.AccessDeniedException("Beheerrechten zijn vereist.");
        }
    }

    private Account account(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Account locked(UUID id) {
        return accounts.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void createAccount(AccountForms.NewAccount form, Role role) {
        accounts.save(newAccount(form, role));
    }

    private Account newAccount(AccountForms.NewAccount form, Role role) {
        String username = normalize(form.username());
        checkUsername(username, null);
        return new Account(username, hash(form.password(), form.confirmation()), form.displayName().strip(),
                role, clock.instant());
    }

    private void checkUsername(String username, UUID exception) {
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,31}")) {
            throw new Problem("Kies een geldige gebruikersnaam van 3 tot 32 tekens.");
        }
        if (accounts.findByUsername(username).filter(a -> !a.getId().equals(exception)).isPresent()) {
            throw new Problem("Deze gebruikersnaam is al in gebruik.");
        }
    }

    private String hash(String password, String confirmation) {
        if (!password.equals(confirmation)) {
            throw new Problem("De nieuwe wachtwoorden komen niet overeen.");
        }
        if (password.isBlank() || password.length() < AccountForms.MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new Problem("Kies een wachtwoord van minimaal " + AccountForms.MIN_PASSWORD_LENGTH + " tekens en maximaal 72 bytes.");
        }
        return encoder.encode(password);
    }

    private static Person person(Account account) {
        return new Person(account.getId(), account.getDisplayName(), account.getAvatar() != null,
                account.isEnabled(), account.getChocolateFlavor(), account.isChildProfile());
    }

    private static AccountView view(Account account) {
        return new AccountView(person(account), account.getUsername(), account.getRole(), account.getColorMode(),
                account.getPalette());
    }

    private static String normalize(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }
}
