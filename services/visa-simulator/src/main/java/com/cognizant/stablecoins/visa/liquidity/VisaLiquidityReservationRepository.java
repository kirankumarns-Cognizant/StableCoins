package com.cognizant.stablecoins.visa.liquidity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VisaLiquidityReservationRepository extends JpaRepository<VisaLiquidityReservation,UUID> {
    interface PocketLocation { String getPocketId(); String getProviderId(); String getCurrency(); }
    @org.springframework.data.jpa.repository.Query("select r.pocketId as pocketId,r.providerId as providerId,r.currency as currency from VisaLiquidityReservation r where r.reservationId=:id")
    Optional<PocketLocation> findPocketLocation(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from VisaLiquidityReservation r where r.reservationId=:id")
    Optional<VisaLiquidityReservation> lockReservation(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<VisaLiquidityReservation> findByPaymentId(String paymentId);
    List<VisaLiquidityReservation> findByPocketId(String pocketId);
}
