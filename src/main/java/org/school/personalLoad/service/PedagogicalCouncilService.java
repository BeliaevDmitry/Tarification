package org.school.personalLoad.service;

import org.school.personalLoad.auth.SessionUser;
import org.school.personalLoad.dto.PedagogicalCouncilDtos;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface PedagogicalCouncilService {

    List<PedagogicalCouncilDtos.ProtocolSummary> list(String academicYear);

    PedagogicalCouncilDtos.ProtocolDetails get(Long id);

    PedagogicalCouncilDtos.ProtocolDetails create(PedagogicalCouncilDtos.CreateProtocolRequest request, SessionUser user);

    PedagogicalCouncilDtos.ProtocolDetails update(Long id, PedagogicalCouncilDtos.UpdateProtocolRequest request, SessionUser user);

    PedagogicalCouncilDtos.ProtocolDetails release(Long id, SessionUser user);

    void deleteProtocol(Long id);

    PedagogicalCouncilDtos.ProtocolDetails uploadArchive(String academicYear,
                                                         String protocolNumber,
                                                         LocalDate meetingDate,
                                                         MultipartFile file,
                                                         SessionUser user) throws IOException;

    PedagogicalCouncilDtos.AttachmentView addAttachment(Long protocolId,
                                                       Long itemId,
                                                       MultipartFile file,
                                                       SessionUser user) throws IOException;

    void deleteAttachment(Long protocolId, Long attachmentId);

    PedagogicalCouncilDtos.FilePayload getAttachment(Long protocolId, Long attachmentId);

    PedagogicalCouncilDtos.FilePayload buildFullProtocol(Long id);

    PedagogicalCouncilDtos.FilePayload buildExtract(Long id,
                                                    PedagogicalCouncilDtos.ExtractRequest request,
                                                    SessionUser user);

    List<PedagogicalCouncilDtos.StaffView> staff();

    List<PedagogicalCouncilDtos.CertifierView> certifiers();
}
