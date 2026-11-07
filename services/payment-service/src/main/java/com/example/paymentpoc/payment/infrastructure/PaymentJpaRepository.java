package com.example.paymentpoc.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    @Query(value = """
            SELECT * FROM payments
            WHERE owner_id = :ownerId
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<PaymentEntity> findFirstPage(
            @Param("ownerId") UUID ownerId,
            @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM payments
            WHERE owner_id = :ownerId
              AND (created_at > :afterCreatedAt
                   OR (created_at = :afterCreatedAt AND id::text > :afterId))
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<PaymentEntity> findNextPage(
            @Param("ownerId") UUID ownerId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") String afterId,
            @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM payments
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<PaymentEntity> findAllFirstPage(@Param("limit") int limit);

    @Query(value = """
            SELECT * FROM payments
            WHERE created_at > :afterCreatedAt
               OR (created_at = :afterCreatedAt AND id::text > :afterId)
            ORDER BY created_at ASC, id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<PaymentEntity> findAllNextPage(
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") String afterId,
            @Param("limit") int limit);
}
