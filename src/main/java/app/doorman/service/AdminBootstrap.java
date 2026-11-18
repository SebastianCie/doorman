package app.doorman.service;

import app.doorman.model.AppUser;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

/** Legt beim ersten Start einen Benutzer "admin" an, wenn noch keiner existiert. */
@ApplicationScoped
public class AdminBootstrap {

    private static final Logger LOG = Logger.getLogger(AdminBootstrap.class);

    @ConfigProperty(name = "doorman.initial-admin-password")
    Optional<String> initialPassword;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (AppUser.count() > 0) {
            return;
        }
        String password = initialPassword.filter(s -> !s.isBlank()).orElse(null);
        boolean generated = password == null;
        if (generated) {
            password = TokenGenerator.newToken().substring(0, 16).toLowerCase();
        }
        AppUser admin = new AppUser();
        admin.username = "admin";
        admin.role = AppUser.ROLE_ADMIN;
        admin.setPassword(password);
        admin.persist();
        if (generated) {
            LOG.warnf("Erster Start: Benutzer 'admin' angelegt mit Passwort '%s'. Bitte nach dem Login ändern!",
                    password);
        } else {
            LOG.info("Erster Start: Benutzer 'admin' mit konfiguriertem Passwort angelegt.");
        }
    }
}
