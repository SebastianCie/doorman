package app.doorman.model;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.util.ModularCrypt;

import java.time.Instant;

@Entity
@Table(name = "app_users")
@UserDefinition
public class AppUser extends PanacheEntityBase {

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER = "user";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Username
    public String username;

    @Password
    @Column(name = "password_hash")
    public String passwordHash;

    @Roles
    public String role;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();

    public static AppUser findByUsername(String username) {
        return find("lower(username) = lower(?1)", username).firstResult();
    }

    public void setPassword(String plain) {
        this.passwordHash = BcryptUtil.bcryptHash(plain);
    }

    public boolean checkPassword(String plain) {
        try {
            PasswordFactory factory = PasswordFactory.getInstance(
                    BCryptPassword.ALGORITHM_BCRYPT, new WildFlyElytronPasswordProvider());
            BCryptPassword stored = (BCryptPassword) factory.translate(ModularCrypt.decode(passwordHash));
            return factory.verify(stored, plain.toCharArray());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
