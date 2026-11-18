package app.doorman.web;

import app.doorman.model.AppUser;
import app.doorman.service.ScanService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/scan")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({AppUser.ROLE_ADMIN, AppUser.ROLE_SCANNER})
public class ScanApi {

    public record ScanRequest(String token, String eventCode, Long checkpointId) {
    }

    /** Check-in ohne QR-Code; {@code verified} = Scanner hat die Personalien geprüft. */
    public record ManualRequest(Long participantId, String eventCode, Long checkpointId, boolean verified) {
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

    @GET
    @Path("search")
    @Consumes(MediaType.WILDCARD)
    public List<ScanService.SearchHit> search(@QueryParam("q") String q, @QueryParam("eventCode") String eventCode,
                                              @QueryParam("checkpointId") Long checkpointId) {
        if (checkpointId == null) {
            throw new BadRequestException();
        }
        return scanService.search(q, eventCode, checkpointId);
    }

    @POST
    @Path("manual")
    public ScanService.ScanResult manual(ManualRequest req) {
        if (req == null || req.checkpointId() == null || req.participantId() == null || !req.verified()) {
            throw new BadRequestException("Personalien müssen geprüft sein");
        }
        return scanService.manual(req.participantId(), req.eventCode(), req.checkpointId(),
                identity.getPrincipal().getName());
    }
}
