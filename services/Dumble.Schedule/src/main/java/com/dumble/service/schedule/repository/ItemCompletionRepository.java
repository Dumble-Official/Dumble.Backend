package com.dumble.service.schedule.repository;

import com.dumble.service.schedule.domain.ItemCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemCompletionRepository extends JpaRepository<ItemCompletion, UUID> {

    boolean existsByItemIdAndCompletedOn(UUID itemId, LocalDate completedOn);

    @Modifying
    @Query("delete from ItemCompletion c where c.itemId = :itemId and c.completedOn = :completedOn")
    int deleteForDate(@Param("itemId") UUID itemId, @Param("completedOn") LocalDate completedOn);

    List<ItemCompletion> findByItemIdInAndCompletedOn(Collection<UUID> itemIds, LocalDate completedOn);
}
