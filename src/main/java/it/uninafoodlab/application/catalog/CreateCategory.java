package it.uninafoodlab.application.catalog;

import it.uninafoodlab.application.course.CourseAuthorizationException;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.util.Objects;

public final class CreateCategory {

    private final CatalogRepository catalogRepository;

    public CreateCategory(CatalogRepository catalogRepository) {
        this.catalogRepository = Objects.requireNonNull(catalogRepository, "catalogRepository");
    }

    public CategorySummary create(AuthenticatedUser actor, NewCategoryInput input) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(input, "input");
        if (actor.role() != Role.CHEF) {
            throw new CourseAuthorizationException();
        }
        return catalogRepository.createCategory(actor.sessionToken(), input);
    }
}
