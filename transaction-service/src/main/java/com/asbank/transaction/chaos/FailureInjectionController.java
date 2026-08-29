package com.asbank.transaction.chaos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty(
        name = "asbank.failure-injection.enabled",
        havingValue = "true"
)
@RestController
class FailureInjectionController {

    @PostMapping("/internal/failure-injection")
    @PreAuthorize(
            "hasAuthority('SCOPE_transaction.write') and " +
                    "hasRole('ADMIN')"
    )
    ResponseEntity<String> inject(
            @RequestParam String mode,
            @RequestParam(
                    defaultValue = "1000"
            ) int value
    ) throws InterruptedException {

        return switch (mode) {
            case "latency" -> latency(value);
            case "error" ->
                    throw new IllegalStateException(
                            "Injected failure"
                    );
            case "memory" -> memory(value);
            default -> ResponseEntity
                    .badRequest()
                    .body("Unknown failure mode");
        };
    }

    private ResponseEntity<String> latency(int milliseconds)
            throws InterruptedException {

        int bounded = Math.clamp(
                milliseconds,
                0,
                5_000
        );

        Thread.sleep(bounded);

        return ResponseEntity.ok(
                "Injected latency: " + bounded + "ms"
        );
    }

    private ResponseEntity<String> memory(int megabytes) {
        int bounded = Math.clamp(
                megabytes,
                1,
                32
        );

        byte[] pressure =
                new byte[bounded * 1024 * 1024];

        for (
                int index = 0;
                index < pressure.length;
                index += 4096
        ) {
            pressure[index] = 1;
        }

        return ResponseEntity.ok(
                "Allocated " + pressure.length + " bytes"
        );
    }
}