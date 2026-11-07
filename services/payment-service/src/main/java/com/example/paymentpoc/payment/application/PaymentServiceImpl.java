package com.example.paymentpoc.payment.application;

import com.example.paymentpoc.payment.domain.*;
import com.example.paymentpoc.shared.exception.InvalidCursorException;
import com.example.paymentpoc.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public PaymentResult create(CreatePaymentCommand command) {
        Money money = new Money(new BigDecimal(command.amount()), command.currency());
        Payment payment = Payment.create(command.ownerId(), money);
        return PaymentResult.from(paymentRepository.save(payment));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResult get(UUID id, UUID ownerId) {
        Payment payment = findOwnedOrThrow(id, ownerId);
        return PaymentResult.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentListResult list(UUID ownerId, String cursor, int pageSize) {
        int fetchSize = pageSize + 1;
        CursorPosition pos = cursor != null ? CursorPosition.decode(cursor) : null;
        List<Payment> fetched = paymentRepository.findByOwner(
                ownerId,
                pos != null ? pos.createdAt() : null,
                pos != null ? pos.id() : null,
                fetchSize);

        boolean hasMore = fetched.size() > pageSize;
        List<Payment> page = hasMore ? fetched.subList(0, pageSize) : fetched;

        String nextCursor = null;
        if (hasMore) {
            Payment last = page.get(page.size() - 1);
            nextCursor = new CursorPosition(last.createdAt(), last.id()).encode();
        }

        return new PaymentListResult(page.stream().map(PaymentResult::from).toList(), nextCursor);
    }

    @Override
    @Transactional
    public PaymentResult cancel(UUID id, UUID ownerId) {
        Payment payment = findOwnedOrThrow(id, ownerId);
        payment.cancel();
        return PaymentResult.from(paymentRepository.save(payment));
    }

    private Payment findOwnedOrThrow(UUID id, UUID ownerId) {
        return paymentRepository.findById(id)
                .filter(p -> p.ownerId().equals(ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id.toString()));
    }

    record CursorPosition(Instant createdAt, UUID id) {

        static CursorPosition decode(String cursor) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = decoded.split(":", 2);
                if (parts.length != 2) throw new IllegalArgumentException("wrong part count");
                long micros = Long.parseLong(parts[0]);
                Instant instant = Instant.ofEpochSecond(micros / 1_000_000L, (micros % 1_000_000L) * 1_000L);
                return new CursorPosition(instant, UUID.fromString(parts[1]));
            } catch (Exception e) {
                throw new InvalidCursorException(cursor);
            }
        }

        String encode() {
            long micros = createdAt.getEpochSecond() * 1_000_000L + createdAt.getNano() / 1_000L;
            String raw = micros + ":" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}
