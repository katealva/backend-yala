package com.yala.service;

import com.yala.dto.identity.ResponseSessionDTO;
import com.yala.dto.seller.RequestSellerApplicationDTO;
import com.yala.dto.seller.ResponseSellerApplicationDTO;
import com.yala.exceptions.DuplicateResourceException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.model.Role;
import com.yala.model.SellerApplication;
import com.yala.model.SellerApplicationStatus;
import com.yala.model.User;
import com.yala.repository.SellerApplicationRepository;
import com.yala.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seller onboarding for already-registered users. Captures the store data and kicks off a
 * Didit KYC session; the user is promoted to SELLER when Didit approves (handled in
 * {@link IdentityService} via the webhook). A user may keep a single PENDING application.
 */
@Slf4j
@Service
public class SellerApplicationService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final UserRepository userRepository;
    private final IdentityService identityService;

    public SellerApplicationService(SellerApplicationRepository sellerApplicationRepository,
            UserRepository userRepository, IdentityService identityService) {
        this.sellerApplicationRepository = sellerApplicationRepository;
        this.userRepository = userRepository;
        this.identityService = identityService;
    }

    @Transactional
    public ResponseSellerApplicationDTO apply(String email, RequestSellerApplicationDTO request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == Role.SELLER || Boolean.TRUE.equals(user.getIsVerifiedSeller())) {
            throw new DuplicateResourceException("Ya eres vendedor.");
        }

        // Reuse a pending application (to retry the KYC) instead of duplicating it.
        SellerApplication application = sellerApplicationRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), SellerApplicationStatus.PENDING)
                .orElseGet(() -> SellerApplication.builder()
                        .user(user)
                        .status(SellerApplicationStatus.PENDING)
                        .build());

        application.setStoreName(request.storeName());
        application.setAddress(request.address());
        application.setPhone(request.phone());
        application.setCci(request.cci());
        application = sellerApplicationRepository.save(application);

        // Start the Didit KYC session. Best-effort: in demo/local (Didit not configured) the
        // application is still saved and the frontend just won't get a KYC url.
        String diditUrl = null;
        String diditSessionId = application.getDiditSessionId();
        try {
            ResponseSessionDTO session = identityService.createSession(email);
            diditUrl = session.url();
            diditSessionId = session.sessionId();
            application.setDiditSessionId(diditSessionId);
            sellerApplicationRepository.save(application);
        } catch (Exception e) {
            log.warn("No se pudo crear la sesión Didit para la aplicación de vendedor de {}: {}",
                    email, e.getMessage());
        }

        return toDto(application, diditUrl);
    }

    @Transactional(readOnly = true)
    public ResponseSellerApplicationDTO getMine(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return sellerApplicationRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .map(app -> toDto(app, null))
                .orElse(null);
    }

    private ResponseSellerApplicationDTO toDto(SellerApplication a, String diditUrl) {
        return new ResponseSellerApplicationDTO(
                a.getId(),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getStoreName(),
                a.getAddress(),
                a.getPhone(),
                a.getCci(),
                a.getCreatedAt(),
                diditUrl,
                a.getDiditSessionId());
    }
}
