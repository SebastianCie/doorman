package app.doorman.web;

import app.doorman.model.AppUser;
import app.doorman.service.ScanService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/scan")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({AppUser.ROLE_ADMIN, AppUser.ROLE_SCANNER})
public class ScanApi {

    public record ScanRequest(String token, String eventCode, Long checkpointId) {
    }

    @Inject
    ScanService scanService;

    @Inject
    SecurityIdentity identity;

    @POST
    public ScanService.ScanResult scan(ScanRequest req) {
        if (req == null || req.checkpointId() == null) {
            throw new BadRequestException();
        }
        return scanService.scan(req.token(), req.eventCode(), req.checkpointId(), identity.getPrincipal().getName());
    }
}
