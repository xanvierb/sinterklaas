package nl.xanvier.sinterklaas.wish;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import nl.xanvier.sinterklaas.shared.Images;
import nl.xanvier.sinterklaas.user.AccountService;
import nl.xanvier.sinterklaas.user.CurrentUser;
import nl.xanvier.sinterklaas.user.FamilyAccess;
import nl.xanvier.sinterklaas.user.LoginUser;
import nl.xanvier.sinterklaas.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WishService {
    public record WishView(UUID id, UUID ownerId, String ownerName, String title, String description, String notes,
                           String productUrl, java.math.BigDecimal estimatedPrice, String currency,
                           Priority priority, String variants, UUID imageId, boolean inStore, String shopName) {}

    private final WishRepository wishes;
    private final AccountService accounts;
    private final CurrentUser current;
    private final Images images;
    private final Clock clock;
    private final FamilyAccess family;
    private final GiftReservations reservations;
    private final ProductMetadataFetcher metadataFetcher;

    WishService(WishRepository wishes, AccountService accounts, CurrentUser current, Images images, Clock clock,
                ProductMetadataFetcher metadataFetcher, GiftReservations reservations, FamilyAccess family) {
        this.wishes = wishes;
        this.accounts = accounts;
        this.current = current;
        this.images = images;
        this.clock = clock;
        this.family = family;
        this.metadataFetcher = metadataFetcher;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public List<AccountService.Person> participants() {
        requireBuyer();
        return accounts.participants();
    }

    public UUID managedOwner(UUID ownerId) {
        LoginUser user = requireListOwner();
        UUID owner = ownerId == null ? user.id() : ownerId;
        if (!family.manages(user, owner)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return owner;
    }

    public List<AccountService.Person> managedChildren() {
        return family.children(requireListOwner().id()).stream().map(accounts::participant).toList();
    }

    public boolean manages(UUID ownerId) {
        return family.manages(current.require(), ownerId);
    }

    @Transactional(readOnly = true)
    public List<WishView> own(UUID ownerId) {
        return views(wishes.findByOwnerIdOrderByPriorityDescUpdatedAtDesc(managedOwner(ownerId)));
    }

    @Transactional(readOnly = true)
    public List<WishView> forParticipant(UUID ownerId) {
        LoginUser viewer = current.require();
        var owner = accounts.participant(ownerId);
        if (!viewer.admin() && viewer.id().equals(ownerId)) {
            return own(ownerId);
        }
        return views(wishes.findByOwnerIdOrderByPriorityDescUpdatedAtDesc(ownerId));
    }

    @Transactional(readOnly = true)
    public WishView editForm(UUID id) {
        Wish wish = wishes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        managedOwner(wish.getOwnerId());
        return view(wish, accounts.participant(wish.getOwnerId()).displayName());
    }

    public ProductMetadataFetcher.ImageData downloadProductImage(String imageUrl) {
        requireListOwner();
        return metadataFetcher.downloadImage(imageUrl);
    }

    public ProductMetadataFetcher.Metadata fetchMetadata(String productUrl) {
        requireListOwner();
        if (productUrl == null || productUrl.isBlank() || productUrl.length() > 2048) {
            throw new nl.xanvier.sinterklaas.shared.Problem("Vul eerst een geldige productlink in.");
        }
        try {
            return metadataFetcher.fetch(productUrl);
        } catch (ProductMetadataFetcher.MetadataUnavailable unavailable) {
            throw new nl.xanvier.sinterklaas.shared.Problem(unavailable.getMessage());
        }
    }

    @Transactional
    public void create(UUID ownerId, WishForms.Input input, MultipartFile photo) {
        UUID owner = managedOwner(ownerId);
        UUID image = images.replace(null, photo);
        wishes.save(new Wish(owner, input, image, clock.instant()));
    }

    @Transactional
    public void update(UUID id, WishForms.Input input, MultipartFile photo) {
        Wish wish = lockedOwnWish(id);
        requireEditable(id);
        UUID image = images.replace(wish.getImageId(), photo);
        wish.update(input, image, clock.instant());
    }

    @Transactional
    public void delete(UUID id) {
        Wish wish = lockedOwnWish(id);
        requireEditable(id);
        images.remove(wish.getImageId());
        wishes.delete(wish);
    }

    @Transactional
    public void updateNotes(UUID id, String notes) {
        Wish wish = lockedOwnWish(id);
        wish.updateNotes(notes);
    }

    private Wish lockedOwnWish(UUID id) {
        Wish wish = wishes.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        managedOwner(wish.getOwnerId());
        return wish;
    }

    private void requireEditable(UUID id) {
        if (reservations.isReserved(id)) {
            throw new nl.xanvier.sinterklaas.shared.Problem(
                    "Deze wens kan niet meer worden gewijzigd of verwijderd. Je kunt wel opmerkingen toevoegen.");
        }
    }

    @Transactional(readOnly = true)
    public UUID image(UUID wishId) {
        Wish wish = visibleWish(wishId);
        if (wish.getImageId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return wish.getImageId();
    }

    @Transactional(readOnly = true)
    public String productUrl(UUID wishId) {
        String url = visibleWish(wishId).getProductUrl();
        if (url == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return url;
    }

    private Wish visibleWish(UUID wishId) {
        Wish wish = wishes.findById(wishId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LoginUser viewer = current.require();
        if (!viewer.admin() && !viewer.id().equals(wish.getOwnerId())) {
            accounts.participant(wish.getOwnerId());
        }
        return wish;
    }

    private LoginUser requireListOwner() {
        LoginUser user = current.require();
        if (user.admin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Beheeraccounts hebben geen persoonlijke wensenlijst.");
        }
        return user;
    }

    private LoginUser requireBuyer() {
        LoginUser user = current.require();
        if (user.admin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Beheeraccounts nemen niet deel aan het cadeauoverzicht.");
        }
        return user;
    }

    private List<WishView> views(List<Wish> data) {
        return data.stream().map(wish -> view(wish, accounts.participant(wish.getOwnerId()).displayName())).toList();
    }

    private WishView view(Wish wish, String ownerName) {
        return new WishView(wish.getId(), wish.getOwnerId(), ownerName, wish.getTitle(), wish.getDescription(),
                wish.getNotes(), wish.getProductUrl(), wish.getEstimatedPrice(), wish.getCurrency(),
                wish.getPriority(), wish.getVariants(), wish.getImageId(), wish.isInStore(), wish.getShopName());
    }

}
