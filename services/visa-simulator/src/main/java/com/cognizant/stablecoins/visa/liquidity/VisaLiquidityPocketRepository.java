package com.cognizant.stablecoins.visa.liquidity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface VisaLiquidityPocketRepository extends JpaRepository<VisaLiquidityPocket,String> {
    @Modifying
    @Query(value="insert into visa_liquidity_pocket(pocket_id,provider_id,currency,total) values (:id,:provider,:currency,0) on conflict (pocket_id) do nothing",nativeQuery=true)
    void ensure(@Param("id") String id,@Param("provider") String provider,@Param("currency") String currency);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from VisaLiquidityPocket p where p.pocketId=:id")
    Optional<VisaLiquidityPocket> lock(@Param("id") String id);
}
